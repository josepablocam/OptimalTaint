#!/usr/bin/env bash

#num_args=$?
num_files=2
min_len=10
out_dir=generated/

phosphor_target=~/MS/research/trippproject/phosphor/Phosphor/target/
phosphor_jar=$(find $1 -iname "Phosphor-[0-9]*SNAPSHOT.jar" | xargs -I {} realpath {})
instrumented_jre=$(realpath ${1}/jre-inst-obj/)

caliper_jar=~/.m2/repository/com/google/caliper/caliper/0.5-rc1/caliper-0.5-rc1.jar
optimal_taint_jar=~/MS/research/trippproject/OptimalTaint/optimal-taint/target/optimal-taint-1.0-SNAPSHOT.jar


function count_lines {
 wc -l $1 | awk '{print $1}'
}

function count_instructions {
  javap -c $1 | count_lines
}

function count_variable_declarations {
  grep "int x_" $1 | count_lines
}

function run {
  if [[ $2 == *_none ]]
    then
      java -cp $1 $2
    else
      $instrumented_jre/bin/java -cp $phosphor_jar:$1 -Xbootclasspath/a:$phosphor_jar -javaagent:$phosphor_jar $2
  fi
}
# Generate files using java
java -cp $optimal_taint_jar com.github.OptimalTaint.Generate $num_files $min_len $out_dir

# Generate google caliper benchmark files
java -cp $optimal_taint_jar com.github.OptimalTaint.CreateCaliperBenchmarks $out_dir "Caliper"

# Change into the folder with all the randomly generated code
cd $out_dir

# Compile all files (random and benchmarks)
mkdir orig_compiled
javac -cp $caliper_jar:$phosphor_jar -d orig_compiled/ *.java

# Instrument a copy of all files (random and benchmarks)
java -jar $phosphor_jar -multiTaint orig_compiled/ inst_compiled/

# Results folder
mkdir results/

# File holding counts data
counts_file=results/instruction_counts.csv
touch $counts_file
echo "name,declaration_count,instruction_count,branches_exec_count" >> $counts_file

## For each randomly generated program (except benchmark files) count important stats
for java_file in $(find . -type f -name "P*.java" -exec basename {} ';')
do
  # trim file ending
  name=${java_file%.*}
  # choose directory to use based on file name
  if [[ $name == *_none ]]
    then
      dir=orig_compiled/
    else
      dir=inst_compiled/
  fi
  # add name of source file
  echo -n $name >> $counts_file
  echo -n "," >> $counts_file
  # count number of variable declarations in original file
  echo -n $(count_variable_declarations $name.java) >> $counts_file
  echo -n "," >> $counts_file
  # count number of bytecode instructions
  echo -n $(count_instructions $dir/$name.class) >> $counts_file
  echo -n "," >> $counts_file
  # count number of branches actually executed
  echo $(run $dir $name) >> $counts_file
done
#
## Caliper benchmark files
# Execution and memory (TODO: memory) benchmarking
# No instrumentation
(cd orig_compiled; ./run_caliper.sh $phosphor_target Caliper_none --results caliper_none_results.json)
## Naive instrumentation
(cd orig_inst; ./run_caliper.sh $phosphor_target Caliper_naive --instrumented --results caliper_naive_results.json)

# TODO: python script to combine json and csv into one dataframe

