.. _atomicrmi-api:

=================================
Application Programming Interface
=================================

A detailed overview of the library's API elements and how to use them to create
a working distributed transactional system.

Components
==========

A typical system using Java RMI consists of a number of JVMs on one or more
hosts. A number of remote objects are created on any of those machines and
registered in an RMI registry located on the same host. A client running on any
JVM will access those remote objects, having first located them via the RMI
registry. Proxies provide rollback capabilities and control method invocations 
to ensure that the properties of atomicity, consistency, and isolation are 
followed.

The components of the system are shown in :ref:`atomicrmi-architecture`, and can
be related to the components of a system using Java RMI but not Atomic RMI in
:ref:`atomicrmi-rmi`.

.. _atomicrmi-architecture:

.. figure:: /img/atomicrmi-architecture.*
    :width: 60%
    :scale: 100%
    :align: center
    :alt: The components of a system using Atomic RMI.

    The components of a system using Atomic RMI

.. _atomicrmi-rmi:

.. figure:: /img/atomicrmi-rmi.*
    :width: 60 %
    :scale: 100%
    :align: center
    :alt: The components of a system using only Java RMI.

    The components of a system using only Java RMI

RMI registry
============

The Atomic RMI library uses the RMI registry both to distribute and to locate
remote objects. Typically, this means using the default implementation of the
interface ``Registry`` from the ``java.rmi.registry`` package,
although other implementations of that interface can be used just as well. 

The registry is an external service that runs on a specific host computer and
listens to a particular port (``1099`` by default). The programmer can gain access
to the registry by using the static ``getRegistry(host, port)`` method of
class ``LocateRegistry`` from the ``java.rmi.registry``. Once the RMI
registry is obtained, the various remote objects can be registered by the
server, or located by the client.

..         *Note:* The way in which the registry is used in version 2.0 of
            Atomic RMI is significantly different to its use in the previous
            versions, where a single global atomic task manager service and a
            specialized versioned registry were required. The former controlled
            the atomic execution of transactions, and the latter helped to
            define task dependencies. The functions of those elements were moved
            to transaction objects and remote objects, respectively.

Remote objects
==============

Remote objects are the shared resources of a distributed system using the 
Atomic RMI library. Remote objects are created by the programmer with very few
restrictions.

All remote objects should implement an interface created by the programmer,
which extends the ``java.rmi.Remote`` interface, for example:

.. code-block:: java

    interface MyRemote extends Remote {
        void doSomething() throws RemoteException;
    }

When extending ``java.rmi.Remote`` all the methods of the interface should
declare to throw ``java.rmi.RemoteException``. This mechanism is used by the
underlying Java RMI framework to move objects from server to server and direct
method invocations.

The following code illustrates how the interface should be implemented:

.. code-block:: java

    class MyRemoteImpl extends TransactionalUnicastRemoteObject implements MyRemote {
        public void doSomething() throws RemoteException {
            ...
        }
    }

It is important to note that all remote objects that are a part of transactional
executions need to extend the class
``soa.atomicrmi.TransactionalUnicastRemoteObject``, which acts as a wrapper and
extends the remote object implementation with counters used by the concurrency
control algorithms, and the ability to create checkpoints to which the objects
can be rolled back.

Objects created from such devised remote object classes must be registered
(bound) with the RMI registry on the server side. This is done with the use of
either the ``bind(name, object)`` method or the ``rebind(name,
object)`` method of the ``Registry`` instance. Then, the objects may be
instantiated on the client side using the ``lookup(name)`` method of the
registry. For example:

.. code-block:: java

    Registry registry = LocateRegistry.getRegistry("localhost");
    MyRemote obj = new MyRemoteImpl();
    registry.rebind("B", b);

.. *Note:* It was the case with the previous version of the library that the
             remote objects had to be used with extreme care, since
             synchronization was placed within the specialized registry, so
             using a remote object after binding it without re-obtaining the
             reference to that object from the registry would invariably lead to
             errors. This is no longer the case, since the synchronization
             mechanisms are grafted into the objects themselves.

Atomic transactions
===================

Distributed atomic transactions are controlled with the use of instances of the
``Transaction`` class from the ``soa.atomicrmi`` package. A transaction object
first needs to be initialized with the constructor, then its preamble must be
defined. Finally, the transaction is started with the method ``start`` and ended
either with the method ``commit``, ``rollback``, or ``retry`` (the latter
requires using the ``Transactable`` interface described later on). Between the
two methods the invocations of remote objects are traced and delayed if
necessary. This guarantees sequential execution of atomic transactions and
guarantees the atomicity constraints. 

The following code shows a fully defined transaction:

.. code-block:: java

    Transaction transaction = new Transaction(registry);
    object = (MyRemote) transaction.accesses(object, 1);
    transaction.start();
    object.doSomething();
    transaction.commit(); // or: transaction.rollback();

The transaction preamble provides information about object accesses which is
necessary for the dynamic scheduling of method calls to remote objects. The
preamble is constructed by calling the method ``accesses(object, calls)`` on the
instance of the transaction for each remote object used in the transaction: the
object reference is passed as the first argument, and the second argument is an
upper bound (supremum) on the number of times the indicated object is used
within the transaction. The method returns an object wrapped by special object
proxy. During transaction execution only this proxy must be used to guarantee
atomicity and isolation properties.

The supremum number of invocations of each object---the second argument of the
``accesses(object, calls)`` method---may be collected manually or inferred
automatically by the precompiler (described in :ref:`atomicrmi-precompiler`). In
case that the exact number is unknown, an upper bound may be given or the number
may be omitted altogether, keeping in mind, that the more relaxed the bounds,
the fewer transactions may be executed in parallel (although the guarantees of
atomicity and isolation are still not violated). If the given number of maximum
method calls is lower than the actual number of calls, the guarantees provided
by the library could not be upheld, and so a ``VersionedRMIException`` exception
is raised.

Atomic RMI provides a mechsanism for releasing a shared object before the
transaction commits by method ``free`` provided by the transaction instance.
This is equivalent to the object reaching its upper bound on executions. Be
advised however, that when ``free`` is used, it becomes the responsibility of
the programmer to ensure that the object is not used again before commit.  

An alternative way of creating a transaction is to use the ``Transactable``
interface from the package ``soa.atomicrmi``, in the following manner:

.. code-block:: java

    Transaction transaction = new Transaction(registry);
    object = (MyRemote) transaction.accesses(object, 1);
    transaction.start(new Transactable() {
        public void atomic(Transaction t) throws RemoteException {
            object.doSomething();
            t.retry();
        }
    });

The programmer implements the ``Transactable`` interface (either by
instantiating an object of an anonymous class or by creating a new class) and
overloads the method ``atomic(transaction)`` using the code that would normally
be inserted between the transaction's `start` and `commit`, `rollback`, or
`retry`, with the exception that ``commit``, ``rollback``, and ``retry`` are
called on the transaction object passed via the method's argument. An instance
of a class implementing the ``Transactable`` interface is then passed as an
argument to the ``start(transactable)`` method of the transaction object. It is
obligatory to use this way of defining transactions to use the retry mechanism.

.. *Note:* The current version of the library redefines remote objects in the
   preamble using wrappers. This is a new mechanism in relation to any previous
   versions.

Lock initialization
===================

Starting transactions (executing a transaction's ``start`` method) may only
happen one-at-a-time within the entire system, because of the operations on
counters of various objects that need to be performed at the time. As a result
of this, a global lock is used at that point. The lock must be initialized on
the server side using the ``initialize(registry)`` static method of the class
``soa.atomicrmi.TransactionsLock``. 

It is important to note that this lock is only held for the short time required
to start transactions and does not play further part in guaranteeing atomicity,
consistency, nor isolation.

.. code-block:: java

    public class Server {
        public static void main(String[] args) {
            
            ...
    
            // Initialize synchronization mechanisms for transactions.
            TransactionsLock.initialize(registry);
        }
    }


Exceptions
==========

A number of exceptions are used within Atomic RMI to alert of unexpected issues:

* ``RetryCalledException`` -- thrown and propagated upwards when a retry
  operation is requested outside of a ``Transactable``-type object,

* ``RollbackForcedException`` -- thrown when a rollback is required during
  remote method invocation or while committing a transaction, and performing the
  invocation or commit would cause an inconsistent state to develop,

* ``TransactionException`` -- thrown when any of a number of serious problems
  occur during the execution transaction. In particular, this means that one of
  the following actions had caused an erroneous situation to occur:

    * `on providing object access information` -- an invalid upper bound on
      number of remote object method invocations was given,

    * `on providing object access information` -- providing object access
      information was attempted while the transaction was in a wrong state
      (e.g., already running),

    * `on providing object access information` -- creation of a proxy for a
      remote object failed (due to an exception on the remote host),

    * `on starting transaction` -- transaction could not be started due to a
      failure on the remote host even though several attempts were made,

    * `on starting transaction` -- transaction could not be initialized because
      the transaction tried to transition from a wrong initial state or to a
      wrong target state,

    * `on starting transaction` -- transaction could not be initialized because
      of an exception on the remote host,

    * `on invoking a remote method` -- the specified upper bound is lower than
      the actual number of invocations,

    * `on invoking a remote method` -- a snapshot of the remote object could not
      be created due an input/output problem on the remote host,

    * `on invoking a remote method` -- a lock to a version counter could not be
      obtained,

    * `on invoking a remote method` -- object could not be freed because of an
      exception on the remote host,

    * `on finishing a transaction` -- a remote object could not be re-created
      from a snapshot due to supplying an inappropriate object type or to an
      input/output problem on the remote host,

    * `on finishing a transaction` -- a lock to a version counter could not be
      obtained,

    * `on finishing a transaction` -- transaction could not be initialized
      because the transaction tried to transition from a wrong initial state or
      to a wrong target state,

    * `on freeing a remote object` -- the object that is to be freed was not
      specified in the preamble, and so this transaction has no access
      information regarding that object,

    * `on finishing a transaction` -- object could not be freed because of an
      exception on the remote host,

    * `on finishing a transaction` -- releasing a transaction lock was attempted
      while the transaction was in a wrong state (e.g., not yet running),

    * `on finishing a transaction` -- releasing a transaction lock was attempted
      while the transaction was not the owner of the lock,

    * `on obtaining the global lock` -- the transaction lock is inaccessible
      because or an exception on the remote host,

.. _Code Generation Library: http://sourceforge.net/projects/cglib/

    ..  Classpath
        ---------

        To use Atomic RMI the following libraries must be included in the classpath:

        * `Code Generation Library`_ (version 2.2), e.g. ``cglib-no-dep-2.2.jar``
        * The Atomic RMI library, e.g. ``atomic-rmi-2.1.jar``


