#!/bin/bash
killall rmiregistry 2>/dev/null

PID=`cat PID`
if [ -n "$PID" ]
then
	kill -9 $PID 2>/dev/null &&	echo "Old processes killed."
fi
echo $$ > PID

PACKAGE=soa.atomicrmi.test.distributed
export CLASSPATH=\
`pwd`:\
`pwd`/bin:\
`pwd`/lib/cglib-nodep-2.2.jar

rmic -d bin $PACKAGE.ImplA || exit 2

echo "Stubs recompiled."

sleep 2

#rmiregistry $2 &
#echo "Registry started."

sleep 2

echo "Starting server..."
java \
    -Djava.rmi.server.hostname=$1 \
    -Djava.rmi.server.codebase=file://`pwd`/bin/ \
    -Djava.security.policy=`pwd`/server.policy \
    $PACKAGE.Server $@
    
echo "Done."
