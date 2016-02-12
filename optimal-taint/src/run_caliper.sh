#!/usr/bin/env bash

## Run a caliper test, both instrumented or not

function help {
  echo "Usage: ./run_caliper.sh <phosphor-target-folder> <class-name> [--compile] [--instrumented] [--results results-file]"
  echo "Running caliper benchmarking"
  echo -e "The instrumented jres are assumed to follow the following naming convention
  jre-inst-obj: Object tags
  The Phosphor jar is assumed to be of the form find Phosphor*SNAPSHOT.jar, where * is the version
  Note that these are the names created by mvn verify in the Phosphor project
  (which calls ./instrumentJRE)"
  }

if [ $# -lt 2 ]
  then
    help
    exit 1
fi

# Necessary Phosphor stuff
phosphor_jar=$(find $1 -iname "Phosphor-[0-9]*SNAPSHOT.jar" | xargs -I {} realpath {})
instrumented_jre=$(realpath ${1}/jre-inst-obj/)
# class to run
name=$2

# Setup for caliper
# I could't get caliper to build, so using maven jars
# TODO: make this not use the maven local repo directly
export CLASSPATH=~/.m2/repository/com/google/caliper/caliper/0.5-rc1/caliper-0.5-rc1.jar:\
~/.m2/repository/com/google/guava/guava/14.0.1/guava-14.0.1.jar:\
~/.m2/repository/com/google/code/gson/gson/2.2.3/gson-2.2.3.jar:\
$phosphor_jar:\
$(pwd)

# consumer optional args if we got any
if [ $# > 2 ]
  then
    shift; shift; # consume first 2 args
    while [[ $# -ge 1 ]]
    do
      key="$1"

      case $key in
        --compile)
        compile=1
        shift
        ;;
        --instrumented)
        instrumented=1
        shift
        ;;
        --results)
        results_file="$2"
        shift; shift # consume both flag and arg
        ;;
        *)
            # unknown option
        ;;
    esac
  done
fi

 # set up necessary flags if instrumenting
if [ ! -z $instrumented ]
  then
  export JAVA_HOME=$instrumented_jre
  export JAVA_TOOL_OPTIONS="-Xbootclasspath/a:$phosphor_jar -javaagent:$phosphor_jar"
fi

if [ ! -z $compile ]
  then
  echo "Compiling $name"
  rm -rf $name.class
  javac $name.java
fi

echo "Bencharking $name"
if [ -z $results_file ]
  then
    java com.google.caliper.Runner $name
  else
    echo "Saving results to $results_file"
    java com.google.caliper.Runner --saveResults $results_file $name
fi
