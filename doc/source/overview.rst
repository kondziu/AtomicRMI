.. _atomicrmi-overview:

========
Overview
========

*Atomic RMI* is an extension of Java Remote Method Invocation with
distributed atomic transactions.

*Java Remote Method Invocation* (Java RMI) is a system for creating distributed
Java technology-based applications, where methods of remote Java objects may be
invoked from other *Java Virtual Machines* (JVMs) on the same host or on
different hosts. Java RMI marshals and unmarshals parameters of remotely-called
methods retaining object-oriented polymorphism and type information.

Our library provides constructs on top of Java RMI allowing the programmer to
declare a series of method calls on remote objects as a distributed transaction.
Such a transaction guarantees the properties of *atomicity* (either all of the
operations of a transaction are performed or none), *consistency* (after any
transaction finishes, the system remain in a valid---or consistent---state), and
*isolation* (each transaction perceives itself as being the only currently
running transaction). *Durability* (when a transaction is completed its effects
are permament) is not normally supported by *Software Transactional Memory*
(STM) in general, and it is also not supported by Atomic RMI.

Atomic RMI exercises pessimistic concurrency control using fine grained locks (a
single lock per remote object) while simultaneously providing support for 
rolling back transactions (using a ``rollback`` construct), and restarting them 
(using a ``retry`` construct). 

This documentation is available electronically from the `Atomic RMI home
page<http://www.it-soa.eu/atomicrmi>` (see the *Papers and reports* section).

    ..  Currently, there is no support for recovery from failures---remote
        object calls that failed due to machine crashes raise an exception,
        which should be properly handled with some compensation action. In the
        future the system will be extended with rollback-recovery and tolerance
        to crashes. 


