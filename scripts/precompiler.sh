#!/bin/bash

# Constants.
ANALYZER_CLASS="soa.atomicrmi.precompiler.Precompiler"
DEFAULT_PROPERTIES="default.properties"
BUILD_PROPERTIES="build.properties"

# Print debug information.
DEBUG=true #$false                                         # Set to 'true' or $false.

# Find Java home dir.
if [ -z "$JAVA_HOME" ]
then
    JAVA_HOME="$(
        update-alternatives --query java | \
        grep -e '^Value:' | \
        cut -f 1 --complement -d ':' | \
        cut -f 1 --complement -d ' '
    )"
    export JAVA_HOME="${JAVA_HOME%/jre/bin/java}"
    if [ -z "$JAVA_HOME" ]
    then
        echo "JAVA_HOME could not be set." >& 2
        exit 1
    fi
fi

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

ATOMIC_RMI_CLASSES="$PWD/$dir_dist/atomicrmi-all-$version.jar"  #$PWD/$dir_build

# Create classpath.
export CLASSPATH="$PWD/$dir_lib/$soot_file:$PWD/$dir_lib/$jasmin_file:$PWD/$dir_lib/$polyglot_file:$ATOMIC_RMI_CLASSES:$PWD"

# Debug.
[ $DEBUG ] && echo "Java home: $JAVA_HOME" >& 2
[ $DEBUG ] && echo "Classpath: $CLASSPATH" >& 2

# Run the precompiler.
java $ANALYZER_CLASS -pp $@
