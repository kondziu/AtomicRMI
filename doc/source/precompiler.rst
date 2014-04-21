.. _atomicrmi-precompiler:

===============================
Automatic inference of accesses 
===============================

*Atomic RMI Precompiler* is a tool which serves to help the users of Atomic RMI
by automatically inferring upper bounds on the number of times each
transactional remote object may be used in each transaction. 

It is a commandline utility which analyzes Java source files (or complete
projects) and, on the basis of the information collected during the analysis,
generates some additional lines of code. This code specifies for each
transaction which objects may be used within that transaction and up to how many
times each of them may be expected to be invoked. These instructions are then
inserted into the source code (either in-place, or into new files) before each
transaction begins.

Before the precompiler is ready to use, it must be built. Detailed instructions
for preparing the precompiler for use are given in
:ref:`atomicrmi-compilation-precompiler`.

Running the precompiler
=======================

TODO

.. code-block:: bash

    java -jar atomicrmi-precompiler-0.2.jar --classpath <CLASSPATH> --sources <SOURCES>

the source code should be correct (able to compile)

.. _atomicrmi-precompiler-options:

Commandline options
===================

TODO

manual


Manual override
===============

When the precompiler generates transactional remote object access information,
any accesses defined in the code by the programmer will be removed and
overwriten. However, the user may override the precompiler's estimation, if a
line of code is annotated with a trailing comment containing the string
``@manual``, as seen in the code example below.

.. code-block:: java

    Transaction transaction = new Transaction(registry);
    object = (MyRemote) transaction.accesses(object, argv.length); // @manual
    transaction.start()

    for (int i = 0; i < argv.length; i++) {
        object.doSomething(argv[i]);
    }

    transaction.retry();

The ``@manual`` string may be changed to any other text at runtime using the
appropriate commandline option described in
:ref:`atomicrmi-precompiler-options`.

