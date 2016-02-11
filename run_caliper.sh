# First arg: phosphor target folder
# Second arg: class name
if [ $# != 2 ]
  then
    echo "Need phosphor target folder and name of class trying to test"
    exit 1
fi


phosphor_jar=$(find $1 -iname "Phosphor-[0-9]*SNAPSHOT.jar" | xargs -I {} realpath {})
instrumented_jre=$(realpath ${1}/jre-inst-obj/)
name=$2

# Setup for caliper
# I could't get caliper to build, so using maven jars
export CLASSPATH=~/.m2/repository/com/google/caliper/caliper/0.5-rc1/caliper-0.5-rc1.jar:\
~/.m2/repository/com/google/guava/guava/14.0.1/guava-14.0.1.jar:\
~/.m2/repository/com/google/code/gson/gson/2.2.3/gson-2.2.3.jar:\
$phosphor_jar:\
$(pwd)

# clean up
rm -f $name.class

# works fine first time around
echo "==> Compiling/Running $name w/o instrumentation"
javac $name.java
java com.google.caliper.Runner $name

# fails when instrumented
export JAVA_HOME=$instrumented_jre
export JAVA_TOOL_OPTIONS="-Xbootclasspath/a:$phosphor_jar -javaagent:$phosphor_jar"
# add to path...maybe that way it'll find java :(
export PATH=$JAVA_HOME/bin:$PATH

# Fails
echo "==> Compiling/Running $name w/ instrumentation"
rm -f $name.class
javac $name.java
java com.google.caliper.Runner $name

