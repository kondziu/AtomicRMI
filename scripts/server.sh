#!/bin/bash
killall rmiregistry 2>/dev/null

# Constants.
PACKAGE=soa.atomicrmi.test.bank
CLASS=Server
DEFAULT_PROPERTIES="default.properties"
BUILD_PROPERTIES="build.properties"

# Convert properties into environment.
TEMP=`mktemp`
cat "$DEFAULT_PROPERTIES" "$BUILD_PROPERTIES" | \
awk -F '=' -v OFS='=' \
    '$1 ~ /^#/ {
        next;
    }
    NF > 1 { 
        gsub(/\./,"_",$1); 
        gsub(/[ \t]*$/,"",$1); 
        sub(/^[ \t]*/, "\"", $2); 
        sub(/[ \t]*$/, "\"", $NF); 
        print "export "$0; 
        next;
    }{}' > "$TEMP"
source "$TEMP"
rm "$TEMP"

# Create classpath.
ATOMIC_RMI_CLASSES=$PWD/$dir_build #"$PWD/$dir_dist/atomicrmi-all-$version.jar"  #$PWD/$dir_build
export CLASSPATH="$PWD:$ATOMIC_RMI_CLASSES:$PWD/$dir_lib/$cglib_file"

echo $CLASSPATH

# Start RMI registry.
PID=`cat .PID`
if [ -n "$PID" ]
then
	kill -9 "$PID" 2>/dev/null &&	echo "Old processes killed."
fi
echo $$ > .PID

sleep 2

rmiregistry $2 &
echo "Registry started."

sleep 2

# Start server.
echo "Starting server..."
java \
    -Djava.rmi.server.hostname=$1 \
    -Djava.rmi.server.codebase=file://$ATOMIC_RMI_CLASSES \
    -Djava.security.policy=$PWD/server.policy \
    $PACKAGE.$CLASS $@
    
echo "Done."
