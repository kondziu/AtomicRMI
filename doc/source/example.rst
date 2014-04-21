.. _atomicrmi-example:

==========================
Complete example: the bank
==========================

The following describes an example showing how to create a simple distributed
application using the Atomic RMI library. 

The example is included with the Atomic distribution RMI and can be run using
 pre-prepared Ant targets.

Remote objects
==============

The example includes a single type of remote object and it is
specified by the interface ``Account``. It provides two methods:
``getBalance`` and ``setBalance`` for determining and setting the bank
account balance.

.. code-block:: java

    public interface Account extends Remote {
        public int getBalance() throws RemoteException;
        public void setBalance(int balance) throws RemoteException;
    }

A class implementing that interface is presented below. The method
``getBalance`` gives the value of the internal field ``balance``. The
``setBalance`` method assigns a new value to this field. 

.. code-block:: java

    public class AccountImpl extends TransactionalUnicastRemoteObject implements Account {
        protected int balance;    
        public AccountImpl(int balance) throws RemoteException {
            this.balance = balance;
        }
        public int getBalance() throws RemoteException {
            return balance;
        }
        public void setBalance(int balance) throws RemoteException {    
            this.balance = balance;
        }
    }


The class ``AccountImpl`` must extend the
``TransactionalUnicastRemoteObject`` class from the ``soa.atomicrmi``
package to allow this remote object to be available remotely and fitted with the
appropriate transactional mechanisms. The standard Java RMI library also allows
to use the static ``ExportObject`` method from the
``UnicastRemoteObject`` class (in such case deriving from class
``UnicastRemoteObject`` is no longer required). This mechanism is not
supported by the Atomic RMI and only the first option can be used.

Server
======

Generally the server implementation should include the following steps:

* Reference to the ``Registry`` must be obtained to allow binding remote
  objects;    

* Remote objects must be instantiated, given identifiers, and registered using
  with the ``Registry`` object;    

* ``TransactionsLock`` must be initialized.


The following server implementation performs those steps in order to create two
     bank accounts. The first is initialized with the balance of 1000 and
     registered as "A". The second is initialized with the balance of 500 and
     registered as "B".

.. code-block:: java

    public class Server {
        public static void main(String[] args) throws RemoteException {
            // Get a reference to RMI registry.
            Registry registry = LocateRegistry.getRegistry("192.168.1.10", 1099);
    
            // Initialize bank accounts.
            Account a = new AccountImpl(1000);
            Account b = new AccountImpl(500);
    
            // Bind addresses.
            registry.rebind("A", a);
            registry.rebind("B", b);
    
            // Initialize synchronization mechanisms for transactions.
            TransactionsLock.initialize(registry);
        }
    }

Audit client
============

Two clients are used to show the usage of atomic transactions. This client
retrieves the balance of accounts A and B, and prints the total balance of those
two accounts. Balance retrieval is done within the atomic transaction. 

To implement those clients these general steps should be followed:

* A reference to the ``Registry`` must be located.

* Remote object references must be located with the use of the ``lookup`` method
  of the ``Registry`` instance.

* New instance of ``Transaction`` must be instantiated.

* The transaction preamble must be described using the ``accesses`` method of
  the ``Transaction`` object and wrapping remote objects code that will
  transparently control the way those objects will be used. (We use new
  variables, ``ta`` and ``tb`` for wrapped objects here, but these objects may
  also be assigned to the old variables ``a`` and ``b`` with which they share a
  common interface.)

* Atomic transaction execution must be contained between the ``start`` method
  and any of the ``commit`` or ``rollback`` methods of the instance of
  ``Transaction``.

The code below implements the first client that is responsible for retrieving
the total balance. In the atomic transaction each of the remote objects is
accessed exactly once and this value is described in the preamble before the
transaction begins. The balance of accounts A and B is retrieved within the
transaction.

.. code-block:: java

    public class AuditClient {
        public static void main(String[] args) throws RemoteException, NotBoundException {
            // Get a reference to RMI registry.
            Registry registry = LocateRegistry.getRegistry("192.168.1.10", 1099);
    
            // Get references to remote objects.
            Account a = (Account) registry.lookup("A");
            Account b = (Account) registry.lookup("B");
            
            // Transaction preamble.
            Transaction transaction = new Transaction(registry);
            Account ta = (Account) transaction.accesses(a, 1);
            Account tb = (Account) transaction.accesses(b, 1);
            
            transaction.start();
            
            // Check balance on both accounts atomically.
            int balanceA = ta.getBalance();
            int balanceB = tb.getBalance();
            
            transaction.commit();
            
            System.out.println(balanceA + balanceB);
        }
    }

When running multiple clients simultaneously from various hosts, using atomic
transactions guarantees that no transfer can be interleaved with any other
transfer or balance retrieval operations, so the total balance is always
constant.

Transfer client
===============

The second client transfers money from account A to account B and commits or
rolls back. This transfer is also done within the atomic transaction. The
implementation of the second client is quite similar:

* A reference to the ``Registry`` is located.

* Remote object references are located with the use of the ``lookup`` method of
  the ``Registry`` instance.

* New instance of ``Transaction`` is instantiated.

* The transaction preamble is described using the ``accesses`` method of the
  ``Transaction`` object and the remote objects are recreated.

* Atomic transaction execution is contained between the ``start`` method and any
  of the ``commit`` or ``rollback`` methods of the instance of ``Transaction``.

This time there are two accesses to remote objects A and B, and this is
accounted for in the task description. Additionally, the transaction can finish
with either a commit, or rollback, depending on some external confirmation. 

.. code-block:: java

    public class TransferClient {
        public static void main(String[] args) throws RemoteException, NotBoundException {
            // Get a reference to RMI registry.
            Registry registry = LocateRegistry.getRegistry("192.168.1.10", 1099);
    
            // Get references to remote objects.
            Account a = (Account) registry.lookup("A");
            Account b = (Account) registry.lookup("B");
    
            // Transaction header.
            Transaction transaction = new Transaction(registry);
            a = (Account) transaction.accesses(a, 2);
            b = (Account) transaction.accesses(b, 2);
    
            transaction.start();
    
            // Retrieve balance on both accounts.
            int balanceA = a.getBalance();
            int balanceB = b.getBalance();
    
            // Transfer funds from A to B.
            a.setBalance(balanceA - 100);
            b.setBalance(balanceB + 100);
    
            // End transaction.
            if (confirm()) {
                transaction.commit();
            } else {
                transaction.rollback();
            }
        }
    }

Transfer client with retry
==========================

This client is functionally the same as the other transfer client, except that it
gives the option to retry the transaction. In order to achieve that, it must use
the ``Transactable`` interface to define the transaction:

* A reference to the ``Registry`` is located.

* Remote object references are located with the use of the ``lookup`` method of
  the ``Registry`` instance.

* New instance of ``Transaction`` is instantiated.

* The transaction preamble is described using the ``accesses`` method of the
  ``Transaction`` object and the remote objects are recreated.

* An object of a class implementing the ``Transactable`` interface is created
  containing the transaction, which is concluded by any of the ``commit``,
  ``retry``, or ``rollback`` methods of the instance of ``Transaction``. (For
  brevity, we create an anonymous class in the example.)

* Atomic transaction execution is commenced when the ``start`` method is called
  with the the ``Transactable`` instance as an argument.

Apart from the possibility of retrying instead of rolling back, the transaction
is identical to that in the `Transfer client`_ without retry.

.. code-block:: java

    public class TransferClient {
        public static void main(String[] args) throws RemoteException, NotBoundException {
                  // Get a reference to RMI registry.
            Registry registry = LocateRegistry.getRegistry("192.168.1.10", 1099);
    
            // Get references to remote objects.
            Account a = (Account) registry.lookup("A");
            Account b = (Account) registry.lookup("B");
    
            // Transaction header.
            Transaction transaction = new Transaction(registry);
            a = (Account) transaction.accesses(a, 2);
            b = (Account) transaction.accesses(b, 2);
    
            transaction.start(new Transactable() {
                public void atomic(Transaction t) throws RemoteException {
                    // Retrieve balance on both accounts.
                    int balanceA = a.getBalance();
                    int balanceB = b.getBalance();
    
                    // Transfer funds from A to B.
                    a.setBalance(balanceA - 100);
                    b.setBalance(balanceB + 100);
    
                    // End transaction.
                    if (confirm()) {
                        t.commit(); 
                    } else { 
                        t.retry(); 
                    }
                }
            });
        }
    }

Running the example
===================

The example is included with the Atomic RMI distribution (extended to wait for
a user's key press before committing or retrying a transaction). Scripts to run
the example where included in the `Apache Ant`_ build file. 

Before running the example, the following conditions must be met:

* the code of the library must be compiled

* the RMI registry must be listening on port 1099 on the same host as the server
  (the host and port may be set using the ``build.properties`` file as describes
  in `Getting started <gettingstarted.html>`_. 

Then, the Ant scripts for each application may be executed for the server:

.. code-block:: bash

    rmiregistry 1099 &                  #start in background
    cd <project-directory>
    ant start-example-server

To run the client described in `Audit client`_:

.. code-block:: bash

    ant start-example-client-audit

To run the client described in `Transfer client`_:

.. code-block:: bash

    ant start-example-client-transfer

To run the client described in `Transfer client with retry`_:

.. code-block:: bash

    ant start-example-client-transfer-retry

.. _Apache Ant: http://ant.apache.org/
.. _Java SDK 1.5: http://www.java.com/
.. _Code Generation Library: http://sourceforge.net/projects/cglib/
.. _jdeb: http://vafer.org/projects/jdeb/


