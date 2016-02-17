package com.github.OptimalTaint;

import java.util.List;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;


public class Generate {
    static final String NAME_SEPARATOR = "_";
    static final String NAME_PREFIX = "P";
    int numFiles;
    int numVars;
    int minLen;
    String outDir;
    RandomProgram randomProgram;

    public void info() {
        System.out.println("Generate " + numFiles + " files, with " + numVars +
                " vars, and at least " + minLen + " commands");
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

    public void run() {
        info();
        // unnecessary, but done for completeness
        Instrumenter none = new NoInstrumenter();
        Instrumenter naive = new NaiveInstrumenter();

        for(int i = 0; i < numFiles; i++) {
            System.out.println("Generating: " + i);
            // use i as seed, note that program state must be fresh each time
            ProgramState programState = new ProgramState(minLen, i);
            // sampled code
            List<String> sampled = randomProgram.sample(programState, numVars);
            // none
            runOne(sampled, programState, none, i, "none");
            // naive
            runOne(sampled, programState, naive, i, "naive");
        }
    }

    public Generate(int numFiles, int numVars, int minLen, String outDir, RandomProgram randomProgram) {
        this.numFiles = numFiles;
        this.numVars = numVars;
        this.minLen = minLen;
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
        System.out.println("usage: <num-files > 0> <num-vars > 0> <min-length > 0> <output-dir> [probs-file]");
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("-help")) {
            help();
            System.exit(0);
        }

        if (args.length < 4) {
            help();
            System.exit(1);
        }

        int numFiles = Integer.parseInt(args[0]);
        int numVars = Integer.parseInt(args[1]);
        int minLen = Integer.parseInt(args[2]);
        String outputDir = args[3];
        Grammar grammar = new UniformGrammar();

        if (numFiles <= 0 || minLen <= 0 || numVars <= 0) {
            help();
            System.exit(1);
        }

        if (args.length == 5) {
            String grammarFile = args[4];
            grammar = new ParsedGrammar(grammarFile);
        }

        // Random program
        RandomProgram randomProgram = new RandomProgram(grammar);
        // Generator
        Generate generate = new Generate(numFiles, numVars, minLen, outputDir, randomProgram);
        // generate programs as requested
        generate.run();
    }
}
