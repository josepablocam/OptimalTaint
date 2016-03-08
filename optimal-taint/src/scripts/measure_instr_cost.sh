#!/usr/bin/env bash

# As per conversations w/ Jon Bell, Phosphor imposes a cost for variables, even if
# not instrumented. This script runs a simple experiment to see if the cost
# is significantly different for non-instrumented variables vs instrumented
# We do this by timing execution of a program that has a large number of variables.
# In one scenario, we instrument them all. In the other, we only instrument one variable
# A large difference in runtime should give us some evidence for a different cost

if [ $# -ne 4 ]
 then
    echo "Usage: ./run_experiments.sh <phosphor-target> <n-files> <generated-dir>"
    echo "phosphor-target: phosphor target folder, as created by calling mvn package; mvn verify; in the phosphor project"
    echo "n-files: number of files to generate and test"
    echo "generated-dir: directory used to create random programs"
    echo "results-dir: directory used to keep results"
    exit 1
fi

# Rewrite a non-instrumented random program to only instrument the first variable
function rewrite {
  local prog=$1
  # Copy necessary imports
  grep "import" $gen_dir/P_0_naive.java > $gen_dir/$prog_modified.java
  # Instrument just one variable
  inst_line=$(grep -n "int x_0" $gen_dir/$prog.java | cut -f1 -d:)
  sed -e "${inst_line}s/= /= MultiTainter.taintedInt(/" $gen_dir/$prog.java |\
  sed -e "${inst_line}s/;/, \"x_0\");/" \
  >> $gen_dir/$prog_modified.java
  mv -f $gen_dir/$prog_modified.java $gen_dir/$prog.java
}

# Arguments
phosphor_target=$(readlink -f $1)
n_files=$2
gen_dir=$(readlink -f $3)
results=$(readlink -f $4)

SCRIPTS_DIR=$(dirname $(readlink -f $0))
optimal_taint_jar=$(readlink -f $SCRIPTS_DIR/../../target/optimal-taint-1.0-SNAPSHOT.jar)
phosphor_jar=$(find $phosphor_target -iname "Phosphor-[0-9]*SNAPSHOT.jar" | xargs -I {} readlink -f {})
caliper_jar=$(readlink -f ~/.m2/repository/com/google/caliper/caliper/0.5-rc1/caliper-0.5-rc1.jar)

# Generate two files with a large number of variables
java -cp $optimal_taint_jar com.github.OptimalTaint.Generate $n_files 1000 400 500 $gen_dir/

# Rewrite files as necessary
for file in $(find $gen_dir -type f -iname "*none.java")
do
  just_name=${file%.java}
  rewrite ${just_name##*/}
done

# Generate google caliper benchmark files
java -cp $optimal_taint_jar com.github.OptimalTaint.CreateCaliperBenchmarks $gen_dir "Caliper"

# Now instrument them
# Compile all files (random and benchmarks)
mkdir $gen_dir/orig_compiled/
javac -cp $caliper_jar:$phosphor_jar -d $gen_dir/orig_compiled/ $gen_dir/*.java

# Instrument a copy of all files (random and benchmarks)
mkdir $gen_dir/inst_compiled/
java -jar $phosphor_jar -multiTaint $gen_dir/orig_compiled/ $gen_dir/inst_compiled/

# Run case where all variables are instrumented
echo "=======> Instrumenting all variables"
(
cd $gen_dir/inst_compiled;
$SCRIPTS_DIR/run_caliper.sh $phosphor_target Caliper_naive --instrumented --results $results/inst_all.json
)

# Run case where only one variable is instrumented
echo "=======> Instrumenting one variable"
(
cd $gen_dir/inst_compiled;
$SCRIPTS_DIR/run_caliper.sh $phosphor_target Caliper_none --instrumented --results $results/inst_one.json
)

