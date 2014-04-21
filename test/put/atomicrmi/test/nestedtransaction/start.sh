#!/bin/bash

PACKAGE=soa.atomicrmi.test.nestedtransaction
export CLASSPATH=\
`pwd`:\
`pwd`/bin:\
`pwd`/lib/cglib-nodep-2.2.jar

java \
    -Djava.security.policy=client.policy \
     $PACKAGE.$@
