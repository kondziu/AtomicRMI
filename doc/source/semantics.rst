.. _atomicrmi-semantics:

=============
In-depth look
=============

A guide to some basic conepts behind Atomic RMI which affect the end user.

Accesses of transactional remote objects
========================================

    .. Version counters for any remote object in the Atomic RMI library are
       counters which are incremented by one on every invocation of any method
       of a particular remote object. The Atomic RMI library traces invocations
       of remote objects' methods and uses the counters to establish whether any
       invocation can proceed or should be delayed, and to try to release
       objects at an early juncture if they will not be used in the transaction
       any more.

It is recommended that an Atomic RMI user provides information about how 
many times, at maximum, each transactional remote object is invoked: this 
information is used to control the way in which remote objects are accessed by
all the transactions in the system.

The maximum number of invocations of each object must either be collected
manually or inferred automatically by the precompiler (described in
:ref:`atomicrmi-precompiler`) for each transaction. It is prefered that the
predicted number of remote object invocations is identical with their actual
number. But if the exact number is unknown, an upper bound may be given or the
number may be omitted altogether, keeping in mind, that the more relaxed the
bounds, the fewer transactions may be executed in parallel (although the
guarantees of atomicity and isolation are still not violated). It is essential
that the number of maximum method calls is never lower than the actual number of
calls, because then the guarantees provided by the library could not be upheld.
If this occurs, ``VersionedRMIException`` exception is thrown.

Manual release
--------------

When an object in a transaction is no longer going to be used a user may indicate so using a manual object release mechanism. 

Distributed transactions
========================

Atomic RMI may be used to create distributed transactions, such where a
transaction started on one host calls methods of objects located on another
host, and those methods involve invoking methods of other remote objects (as
shown in `atomicrmi-distributed`_). 

.. _atomicrmi-distributed:

.. figure:: /img/atomicrmi-distributed.*
    :width: 60%
    :scale: 100%
    :align: center
    :alt: Distributed transactions.

    Distributed transactions

When creating distributed transactions it is also necessary for the programmer
to declare maximum accesses to all remote objects, even those used within other
remote objects' methods. Atomic RMI provides no additional mechanisms to
facilitate this, but a simple addition to the remote objects' interface can be
used to mitigate the inconvenience. 

First, the programmer may declare a method that returns all the other remote
objects that a given remote object uses. For simple cases just the reference to
an object can be returned, while a more complex case can return a collection
indicating both which objects are used and up to how many times. The following
example illustrates an interface which declared two methods, ``a`` and ``b``,
which do something that uses some remote objects, and two more methods,
``getObjectForA`` and ``getAccessesForB`` which return information about remote
object accesses performed in ``a`` and ``b``.

.. code-block:: java

    interface DistributedRemote extends Remote {
        void a();
        void b();
        Remote getObjectForA();
        Map<Remote, Integer> getAccessesForB();
    }


When a transaction is declared using ``DistributedRemote``-type objects, the
accesses will include information about how many times those object may be used
at maximum, and also at-most how many times the objects used within their
methods may be used. For example:

.. code-block:: java

    Transaction t = new Transaction(registry);

    DistributedRemote r = (DistributedRemote) registry.lookup("A");

    // Which remote objects are used from this host and how many times?
    r = (DistributedRemote) t.accesses(r, 2);

    // Variant 1: which remote object does r use whenever we call the method a?
    t.accesses(r.getObjectForA());

    // Variant 2: which remote objects does r use whenever we call the method b 
    // and how many times?
    Map<Remote, Integer> accessesForB = t.getAccessesForB();
    for (Remote object : accessesForB.keySet()) {
        Integer upperBound = accessesForB.get(object);
        t.accesses(object, upperBound);
    }

    t.start();

    r.a();
    r.b();

    t.commit();


An example implementation of that interface is presented below:

.. code-block:: java

    class DistributedRemoteImpl extends TransactionalUnicastRemoteObject implements DistributedRemote {

        private MyRemote o1, o2, o3;

        DistributedRemoteImpl(MyRemote o1, MyRemote o2, MyRemote o3) {
            this.o1 = o1;
            this.o2 = o2;
            this.o3 = o3;
        }

        public void a() { 
            o1.doSomething(); 
        }

        public void b() {
            o1.doSomething();
            o2.doSomething();
            o3.doSomething();
        }

        public Remote getObjectForA() {
            return o1;
        }

        public Map<Remote, Integer> getAccessesForB() {
            Map<Remote, Integer> map = new HashMap<Remote, Integer>();
            map.put(o1, 1);
            map.put(o2, 1);
            map.put(o3, 1);
            return map;
        }
    }


Multi-threaded transactions
===========================

Atomic RMI does not make any allowances for threads started within transactional
code. A multi-threaded transaction may be created, but all matters of
synchronization are then left to the programmer. These include:

* making certain that no thread invokes the ``commit``, ``rollback``, or
  ``retry`` methods after another thread had done so, which will confuse the
  state of the transaction, and cause an exception to be raised;
* making certain that no thread tries to access any transactional remote objects
  after any thread invokes a ``commit``, ``rollback``, or ``retry`` method,
  which may leave the system in an incoherent state or cause other unforseen
  problems;
* ensuring that the maximum number of object accesses is properly declared no
  matter how the operation within the threads are interwoven, because a declared
  number of accesses lower than their actual number, the guarantees described in
  :ref:`atomicrmi-overview` may be violated and an exception will be raised.

        .. A simple solution dealing with these problems would be to make sure
           all the threads spawned after the transaction had started (the
           ``start`` method has been called) end before it is committed,
           rolled-back or retried.    

In addition to the problems mentioned above, it is necessary for the programmer
to create any synchronization mechanisms that may be required, as Atomic RMI
provides none for threads within a single transaction.

Note that running transactions in a separate thread while the entire transaction
is completely within that thread causes no issues to arise.

Nested transactions
===================

Although it is not recommended, it is possible for an Atomic RMI user to nest
transactions within other transactions. In such cases it is vital for the
programmer to ensure that an inner transaction does not use any of the
transactional objects used by outer transactions. If this condition is not
ensured, Atomic RMI will cause a deadlock. 

    .. XXX deadlock?

Recurrency
==========

Atomic RMI allows recurrent starting of the same transactional code, provided
the following conditions are met:

* the transaction is defined using the ``Transactable`` interface (as described
  in: :ref:`atomicrmi-api`),
* the maximum possible number of accesses of remote objects' methods in
  recurring invocations are accounted for in the transactions preamble,

Then, the programmer can simply call the method ``atomic`` again within itself
to create recursion. 

The execution will proceed until the methods ``commit``, ``rollback``, or
``retry`` are called, in which case the ``atomic`` method is exited and the
transaction finishes as normal.

In case when the programmer does not use the ``Transactable`` interface to
define a transaction, calling the method ``start`` multiple times will not
result in recursion, but instead an exception will be raised at run-time.

Failures
========

Atomic RMI can suffer two basic types of failures: failures of remote objects
and failures of transactions.

Failures of remote objects
--------------------------

Failures of remote objects are straightforward and the responsibility for
detecting them and alarming Atomic RMI falls onto the mechanisms built into Java
RMI. Whenever a remote object is called from a transaction and it cannot be
reached, it is assumed that this object has suffered a failure and as a result a
``RemoteException`` is thrown at run-time. The programmer may then choose to
handle that exception by, for example, rolling the transaction back, re-running
it, or compensating for the failure.

Failures of remote objects follow a *crash-stop* model, where an object that has
crashed is not brought back to operation, but simply removed from the system.

Failures of transactions
------------------------

Failures of transactions are such failures where remote objects that are
operated on by a transaction loose communuication to that transaction and the
transaction is considered to have crashed. When such a failure is detected the
affected remote objects revert to the state immediately prior to the start of
the transaction, so that all changes done by the failed transaction are
forgotten.



.. this is entirely the wrong level of detail for the purpose of this document!

        An introduction to internal works of Atomic RMI.

        Versioning algorithms
        =====================

        Atomic RMI uses pessimistic concurrency control based on versioning
        algorithms: version counters for any remote transactional object in
        Transaction processing are incremented by one on every invocation of any
        method of a particular remote object. The Atomic RMI library traces
        invocations of remote objects' methods and uses the counters to
        establish whether any invocation can proceed or should be delayed until
        the counters indicate that no other transaction is currently using the
        same remote objects' methods. This is a mode of operation following the
        `Basic Versioning Algorithm` (BVA) depicted in :ref:`atomicrmi-bva`, and
        it requires that the remote objects used by each transaction be
        specified before that transaction is started.

        .. _atomicrmi-bva:

        .. figure:: /img/atomicrmi-bva.*
            :width: 60%
            :alt: Transaction scheduling using BVA.

            Transaction scheduling using BVA

        Furthermore, if the maximum expected number of invocations to each
        transactional remote object is given prior to the execution of a given
        transaction, it becomes possible for the Atomic RMI library to determine
        that a remote object will no longer be used after a given execution and
        release that objects before the transaction itself finishes its
        processing. This follows the `Supremum Versioning Algorithm` (SVA) shown
        in :ref:`atomicrmi-sva`. This is a more efficient scheduling algorithm
        than BVA, but requires that the user provides more information before
        transactions are run.

        .. _atomicrmi-sva:

        .. figure:: /img/atomicrmi-sva.*
            :width: 60%
            :alt: Transaction scheduling using SVA.

            Transaction scheduling using SVA


        In practice, the Atomic RMI library switches between SVA and BVA on a
        transaction-level, depending on how detailed the information provided in
        the preamble to each transaction are: if upper bounds on invocations of
        all remote objects are specified, SVA is applied, and otherwise, Atomic
        RMI uses BVA. .. *Note:* Versions were referred to as dependencies in
        the previous versions of the manual. Furthermore, there existed two
        separate mechanisms to assert dependencies: one to describe remote
        object dependencies and the other for atomic task dependencies.
        Currently, only the methods described above were deemed useful. 

