package com.github.OptimalTaint;

import java.io.*;
import java.util.List;


public class Generate {
    static final String NAME_SEPARATOR = "_";
    static final String NAME_PREFIX = "P";
    int numFiles;
    int numVars;
    int minLen;
    int minExecutions;
    String outDir;
    RandomProgram randomProgram;

    public void info() {
        System.out.println("Generate " + numFiles + " files, with " + numVars +
                " vars, at least " + minLen + " commands, at least " + minExecutions +
                " commands executed");
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
            ProcessBuilder compile = new ProcessBuilder("javac", fileName);
            compile.start().waitFor();
            ProcessBuilder runner = new ProcessBuilder("java", "-cp", outDir, name);
            Process running = runner.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(running.getInputStream()));
            ctInstructionExecutions = Integer.parseInt(reader.readLine());
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
