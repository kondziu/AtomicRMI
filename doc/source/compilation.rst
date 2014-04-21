.. _atomicrmi-compilation:

==========================
Compilation and deployment
==========================

A quick overview on what is necessary to compile the library and how to start
the example which is included in the distribution.

Deployment requirements
=======================

Atomic RMI requires the following components to be installed on the target
system:

* `Java SDK 1.5`_ or later
* `Code Generation Library`_ (cglib) 2.2 (distributed under the Apache License
  2.0)

Additionally, the following are optional but helpful tools in compilation and
packaging:

* `Apache Ant`_ -- used to run all build scripts (distributed under the Apache
  License 2.0)
* `jdeb`_ 0.7 or later -- used to allow Ant to create debian packages
  (distributed under the Apache License 2.0)

Building the library
====================

Atomic RMI can be compiled using `Apache Ant`_, a popular Java-based build tool.
Prior to building, the ``build.properties`` configuration file can be
adjusted:

* ``cglib.file`` -- name of the file containing the `Code Generation Library`_
* ``cglib.version`` -- version of the `Code Generation Library`_
* ``jdeb.file`` -- name of the file containing the `jdeb`_ library
* ``dir.lib`` -- directory containing all external libraries
* ``dir.src`` -- directory containing source code
* ``dir.test`` -- directory containing source code of tests and examples
* ``dir.doc`` -- directory containing the documentation
* ``dir.build`` -- directory where Java binaries (class files) will be generated
* ``dir.dist`` -- directory where distribution packages (``deb``, ``rpm``,
  ``jar``) will be generated
* ``dir.javadoc`` -- directory where Javadoc documentation will be generated
* ``dir.deb`` -- temporary directory used to create a ``deb`` package
* ``dir.rpm`` -- temporary directory used to create an ``rpm`` package
* ``javadoc.title.window`` -- title of all Javadoc pages
* ``javadoc.title.document`` -- title of the main Javadoc document
* ``javadoc.bottom`` -- footer of all Javadoc documents
* ``dir.system.jar`` -- path for deploying generated ``jar`` files on Unix
  systems.
* ``dir.system.doc`` -- path for deploying documentation on Unix systems.
* ``example.host`` -- hostname used in the bank example 
* ``example.port`` -- port used in the bank example

A number of typical Ant targets are available:

* ``all`` -- run all build targets
* ``compile`` -- compile the library, tests, and examples
* ``dist`` -- create all distribution packages
* ``jar`` -- create a Java archive from compiled sources
* ``deb`` -- create a Debian package from compiled sources
* ``rpm`` -- create a Red Hat package from compiled sources
* ``tar`` -- create a tarball from source code
* ``javadoc`` -- generate Javadoc API documentation
* ``html`` -- generate HTML documentation using Sphinx
* ``clean`` -- remove all generated files

Running the ``ant`` command in the project's directory with no arguments or with
the ``all`` target will create a distribution-ready set of packages:

.. code-block:: bash

    cd <project-directory>
    ant all

A successful creation of distribution packages can be given a crash test using
the example provided in the source code (described in :ref:`atomicrmi-example`.)  

.. _atomicrmi-compilation-precompiler:

Adding the precompiler
=========================

To use the tool for automatic inference of transactional remote object access 
information (described in :ref:`atomicrmi-precompiler`), the following 
additional considerations need to be taken when deploying Atomic RMI.

The precompiler requires the following components to be installed on the target
system:

* `Java SDK 1.5`_ or later
* `Soot`_ 2.4.0 or later (distributed under the GNU Lesser General Public
  License 2.1)
* `Jasmin`_ 2.4.0 or later (required by `Soot`_, distributed under the GNU
  General Public License)
* `Polyglot`_ 2.4.0 or later (distributed under the GNU Lesser General Public
  License 2.1 and the Eclipse Public License 1.0)


The ``precompiler.properties`` configuration file can be adjusted prior to
building:

* ``dir.precompiler.src`` -- directory containing source code of the precompiler
* ``dir.precompiler.lib`` -- directory containing all external libraries
  required by the precompiler
* ``dir.precompiler.build`` -- directory where Java binaries (class files) will
  be generated

Afterwards, the precompiler can be built using Atomic RMI's Ant build script.
The following targets are precompiler-specific:
    
* ``precompiler-all`` -- create distribution packages for the precompiler
* ``precompiler-compile`` -- compile the precompiler
* ``precompiler-dist`` -- create distribution packages for the precompiler
* ``precompiler-jar`` -- create a Java archive from compiled sources

Running the ``ant`` command in the project's directory with 
the ``precompiler-all`` target will create a ready-to-use executable jar file:

.. code-block:: bash

    cd <project-directory>
    ant precompiler-all

.. _jdeb: http://vafer.org/projects/jdeb/
.. _Apache Ant: http://ant.apache.org/
.. _Java SDK 1.5: http://www.java.com/
.. _Code Generation Library: http://sourceforge.net/projects/cglib/
.. _Soot: http://www.sable.mcgill.ca/soot/
.. _Jasmin: http://jasmin.sourceforge.net/
.. _Polyglot: http://www.cs.cornell.edu/projects/polyglot/

