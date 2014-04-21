#!/bin/bash

PID=`cat PIDm`
if [ -n "$PID" ]
then
	kill -9 $PID 2>/dev/null &&	echo "Old processes killed."
fi
echo $$ > PIDm

PACKAGE=soa.atomicrmi.test.distributedtransaction
export CLASSPATH=\
`pwd`:\
`pwd`/bin:\
`pwd`/lib/cglib-nodep-2.2.jar

echo "Starting server..."
java \
    -Djava.rmi.server.hostname=$1 \
    -Djava.rmi.server.codebase=file://`pwd`/bin/ \
    -Djava.security.policy=`pwd`/server.policy \
    $PACKAGE.MultiServer $@
    
echo "Done."
