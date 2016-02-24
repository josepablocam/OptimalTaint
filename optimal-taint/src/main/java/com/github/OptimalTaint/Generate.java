package com.github.OptimalTaint;

import java.io.*;
import java.util.List;


public class Generate {
    static final String NAME_SEPARATOR = "_";
    static final String NAME_PREFIX = "P";
    // time outs to get rid of long running random programs
    static final long COMPILE_TIMEOUT = 10L;
    static final long EXEC_TIMEOUT = 2L;
    // GNU core utils timeout's code for process that timed out
    static final int TIMEOUT_CODE = 124;
    int numFiles;
    int numVars;
    int minLen;
    int minExecutions;
    String outDir;
    RandomProgram randomProgram;

    public void info() {
        System.out.println("Generate random programs");
        System.out.println("Num Files: " + numFiles);
        System.out.println("Num Vars: " + numVars);
        System.out.println("Min Cmds: " + minLen);
        System.out.println("Min Execs: " + minExecutions);
        System.out.println("Compile timeout(s): " + COMPILE_TIMEOUT);
        System.out.println("Exec timeout(s): " + EXEC_TIMEOUT);
        System.out.println("Output directory: " + outDir);
    }

    private String makeName(int i, String suffix) {
        return NAME_PREFIX + NAME_SEPARATOR + i + NAME_SEPARATOR + suffix;
    }

    private void runOne(List<String> code,
                        ProgramState progState,
                        Instrumenter instrumenter,
                        int progCounter,
                        String instrumentationType) {
        String name = makeName(progCounter, instrumentationType);
        List<String> instrumented = instrumenter.instrument(progState, code);
        String assembled = randomProgram.assembleCode(name, instrumented, instrumentationType);
        try {
            File file = new File(outDir + File.separator + name + ".java");
            Writer writer = new BufferedWriter(new FileWriter(file));
            writer.write(assembled);
            writer.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public boolean verifyRunProgram(int i, String nameEnding) {
        String name = makeName(i, nameEnding);
        String fileName = outDir + File.separator + name + ".java";
        int ctInstructionExecutions = 0;
        try {
            // compile and run our sample program and use stdout print
            // to gauge if it has enough computation
            // using GNU timeout to avoid long running random programs
            // we don't use waitFor (which has timeout option in java 1.8),
            // as java 1.8 conflicts with other parts of our experimental pipeline
            ProcessBuilder compile = new ProcessBuilder("timeout", COMPILE_TIMEOUT + "s", "javac", fileName);
            Process compiling = compile.start();
            compiling.waitFor();
            if (compiling.exitValue() == TIMEOUT_CODE) {
                System.out.println("Compilation timed out");
            }

            ProcessBuilder runner = new ProcessBuilder("timeout", EXEC_TIMEOUT + "s", "java", "-cp", outDir, name);
            Process running = runner.start();
            running.waitFor();
            if (running.exitValue() == TIMEOUT_CODE) {
                System.out.println("Execution timed out");
            }

            // just use the last line to validate execution count
            BufferedReader reader = new BufferedReader(new InputStreamReader(running.getInputStream()));
            String lastLine = "";
            String currLine = "";

            while ((currLine = reader.readLine()) != null) {
                lastLine = currLine;
            }
            ctInstructionExecutions = Integer.parseInt(lastLine);
        } catch (Exception e) {
            System.out.println("Failed to build");
            System.out.println(e.getMessage());
        }
        return ctInstructionExecutions >= minExecutions;
    }

    public void run() {
        info();
        // unnecessary, but done for completeness
        Instrumenter none = new NoInstrumenter();
        Instrumenter naive = new NaiveInstrumenter();

        // keep track of a seed offset to avoid repeating files that
        // didn't result in enough instructions being executed
        int seed_offset = 0;

        for(int i = 0; i < numFiles; i++) {
            int seed = i + seed_offset;
            System.out.println("Generating: " + i + " w/ seed:" + seed);
            // use i as seed, note that program state must be fresh each time
            ProgramState programState = new ProgramState(minLen, seed);
            // sampled code
            List<String> sampled = randomProgram.sample(programState, numVars);
            // none
            runOne(sampled, programState, none, i, "none");
            boolean enoughComputation = verifyRunProgram(i, "none");
            // naive
            if (enoughComputation) {
                runOne(sampled, programState, naive, i, "naive");
            } else {
                // adjust seed to avoid repeating, decrease i to overwrite
                // the file that didn't meet expectations
                System.out.println("Not enough computation, regenerating");
                seed_offset++;
                i--;
            }
        }
    }

    public Generate(int numFiles, int numVars, int minLen, int minExecutions, String outDir, RandomProgram randomProgram) {
        this.numFiles = numFiles;
        this.numVars = numVars;
        this.minLen = minLen;
        this.minExecutions = minExecutions;
        this.outDir = outDir;
        // if outdir doesn't exist, create it
        File f = new File(outDir);
        if (!f.exists() && !f.mkdirs()) {
            System.out.println("Unable to generate out directory");
            System.exit(1);
        }
        this.randomProgram = randomProgram;
    }

    private static void help() {
        System.out.println("usage: <num-files > 0> <num-vars > 0> <min-length > 0> <min-executions > 0> <output-dir> [probs-file]");
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("-help")) {
            help();
            System.exit(0);
        }

        if (args.length < 5) {
            help();
            System.exit(1);
        }

        int numFiles = Integer.parseInt(args[0]);
        int numVars = Integer.parseInt(args[1]);
        int minLen = Integer.parseInt(args[2]);
        int minExecutions = Integer.parseInt(args[3]);
        String outputDir = args[4];
        Grammar grammar = new UniformGrammar();

        if (numFiles <= 0 || minLen <= 0 || numVars <= 0) {
            help();
            System.exit(1);
        }

        if (args.length == 6) {
            String grammarFile = args[5];
            grammar = new ParsedGrammar(grammarFile);
        }

        // Random program
        RandomProgram randomProgram = new RandomProgram(grammar);
        // Generator
        Generate generate = new Generate(numFiles, numVars, minLen, minExecutions, outputDir, randomProgram);
        // generate programs as requested
        generate.run();
    }
}
