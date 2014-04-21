#!/bin/bash

# Constants.
PACKAGE=soa.atomicrmi.test.bank
CLASS=Audit
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

# Run client.
java -Djava.security.policy=client.policy $PACKAGE.$CLASS $@
