# This simple script combines the csv and json data outputs from the experiments
# related to benchmarking, and consolidates them into a single csv file
# Furthermore, note that the results are generated for each experiment iteration (caliper overwrites
# prior results), so this script should be called after each iteration in order to append
# results to a main results file
import pandas as pd
import json
import os
import sys


def parse_csv(filepath):
    """
    Create a dataframe from the counts data at filepath
    :param filepath:
    :return: dataframe with counts information for a particular benchmark
    """
    return pd.DataFrame.from_csv(filepath).reset_index()


def _get_caliper_time(bench):
    """
    Collect the processed time associated with a benchmark from caliper json files.
    The bench is assumed to be the node at the path root -> 'run' -> 'measurements' in the
    json output file
    :param benchmark_json:
    :return: array of times collected for a particular microbenchmark in caliper
    """
    return [iter['processed'] for iter in  bench['v']['measurementSetMap']['TIME']['measurements']]

def parse_json(filepath):
    """
    Create a dataframe from caliper json files
    :param filepath:
    :return: dataframe of caliper results
    """
    with open(filepath) as fhandle:
        data = json.load(fhandle)

    # environment information
    env_info_keys = ['jre.version', 'jre.availableProcessors', 'os.name', 'os.version']
    env_info = { key : [data['environment']['propertyMap'][key]] for key in env_info_keys }
    env_df = pd.DataFrame(env_info)

    # now actually get the experiment timing/memory info
    measurements = []
    for bench in data['run']['measurements']:
        # benchmark name
        name = bench['k']['variables']['benchmark']
        # TODO: ADD MEMORY USAGE HERE AS WELL
        df = pd.DataFrame({ 'name': name, 'execution_time' : _get_caliper_time(bench)})
        measurements.append(df)
    measurements_df = pd.concat(measurements, axis = 0).reset_index()
    return pd.concat([measurements_df, env_df], axis = 1).fillna(method = 'ffill')


def merge(counts, caliper):
    """
    Combined counts dataframe and caliper benchmark dataframe on the basis of the name of the
    file/benchmark
    :param d1:
    :param d2:
    :return: merged data frame
    """
    return pd.merge(counts, caliper, how = 'inner', on = 'name')


def writeout(path, data):
    """
    If `path` file already exists, then appends to end, else creates file and writes results
    :param path:
    :param data:
    :return: void
    """
    if os.path.exists(path):
        fhandle = open(path, 'a')
        data.to_csv(fhandle, index = False, header = True)
    else:
        fhandle = open(path, 'w')
        data.to_csv(fhandle, index = False, header = True)
    fhandle.close()

def main(counts_path, caliper_path_stub, output_path):
    """
    Combine results collected by custom counts, caliper and write out to disk
    :param count_path: path for counts .csv file
    :param caliper_path_stub: a stub of the path for caliper results (excluding portion indicating
    the type of benchmarking and file ending). e.g.
    OptimalTaint/optimal-taint/results/caliper_results_none.json =>
    OptimalTaint/optimal-taint/results/caliper_results
    :param output_path: path for file where data is being collected in each experiment iteration
    :return: void
    """
    counts_data = parse_csv(counts_path)
    no_tracking = parse_json(caliper_path_stub + "_none.json")
    naive_tracking = parse_json(caliper_path_stub + "_naive.json")
    caliper_data = pd.concat([no_tracking, naive_tracking], axis = 0).reset_index()
    complete_data = merge(counts_data, caliper_data)
    writeout(output_path, complete_data)

def help_message():
    print "Usage: python combine_results.py <counts-path> <caliper_path_stub> <combined-path>"

# parse args
if __name__ == "__main__":
    if len(sys.argv) != 4:
        help_message()
        sys.exit()
    else:
        counts_path = sys.argv[1]
        caliper_path_stub = sys.argv[2]
        combined_path = sys.argv[3]
        main(counts_path, caliper_path_stub, combined_path)
