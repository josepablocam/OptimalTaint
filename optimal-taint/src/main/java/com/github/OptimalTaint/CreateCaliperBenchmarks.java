package com.github.OptimalTaint;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * This class takes a directory of randomly generated programs (generated using Generate)
 * and creates a .java file containing a class extending caliper's benchmark class.
 * The file contains each of the randomly generated programs as private classes.
 * Meanwhile, the benchmark class contains timeX methods, where X corresponds to the name
 * of the original program. Each timeX method calls the static .run() method for the appropriate
 * random class in a for-loop.
 *
 */
public class CreateCaliperBenchmarks {
    // Pattern to identify import statements in programs
    private final static String IMPORTS_REGEX = "import (.*)";
    // modify class names to avoid conflicts by appending this
    private final static String CLASS_NAME_DISAMBIG = "_";

    /**
     * given a directory, return all files that match a given ending
     * @param dirName directory name
     * @param ending ending to be matched
     * @return
     */
    private static File[] getFiles(String dirName, final String ending) {
        File dir = new File(dirName);
        return dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(ending);
            }
        });
    }

    /**
     * Given a series of file names, get class names (simply splits at period and takes first part)
     * @param files
     * @return
     */
    private static List<String> getClassNames(File[] files) {
        List<String> classNames = new ArrayList<String>();
        for (File file : files) {
            classNames.add(file.getName().split("\\.")[0]);
        }
        return classNames;
    }

    /**
     * Get code from a file. If `justImports` is true, then it simply returns a string
     * of the import statements, if not, then it returns all code EXCEPT the import statements
     * @param file to read code from
     * @param justImports whether to return only import statements or only non-import code
     * @return
     */
    private static String getCode(File file, boolean justImports) {
        // We need different names to avoid class files getting replaced if in same folder
        // when compiling
        String origClassName = file.getName().split("\\.")[0];
        String newClassName = origClassName + CLASS_NAME_DISAMBIG;

        StringBuilder code = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                boolean isImport = line.matches(IMPORTS_REGEX);
                // replace any mentions of the class name
                line = line.replaceAll(origClassName, newClassName);

                if (justImports && isImport) {
                    code.append(line);
                    code.append("\n");
                }

                if (!justImports && !isImport){
                    code.append(line);
                    code.append("\n");
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return code.toString();
    }

    /**
     * Simple wrapper to getCode that simply returns import statements
     * @param file file to read import statements from
     * @return
     */
    private static String getImportStatements(File file) {
        return getCode(file, true);
    }

    /**
     * Given a className (corresponding to a randomly generated class), creates a timeX method
     * for caliper benchmarking, where X is replaced with the class name. It calls the static
     * .run method in a for loop, and accumulates the value (to avoid dead code elimination)
     * in a long variable, which it returns on exit.
     * @param className name of randomly generated class
     * @returns code for time method to be added to caliper class
     */
    private static String createTimeMethod(String className) {
        String methodSignature = "public long time" + className + "(int reps)";
        String body = "\tlong x = 0;\n";
        body += "\tfor (int i = 0; i < reps; i++) {\n";
        body += "\t\tx += " + className + CLASS_NAME_DISAMBIG + ".run();\n";
        body += "\t}\n";
        body += "\treturn x;\n";
        return methodSignature + "{\n" + body + "}\n";
    }

    /**
     * Creates a caliper 0.5 class extending SimpleBenchmark, and adds a timing method for
     * each randomly tested code.
     * @param caliperClassName name for public caliper class for benchmarking
     * @param files files to read random code from and add to benchmarking
     * @return
     */
    private static String createCaliperCode(String caliperClassName, File[] files) {
        String caliperImports = "import com.google.caliper.SimpleBenchmark;\n";
        List<String> classNames = getClassNames(files);
        String otherImports = getImportStatements(files[0]);
        StringBuilder randomCode = new StringBuilder();

        for (File file : files) {
            randomCode.append(getCode(file, false));
        }

        StringBuilder benchmarkCode = new StringBuilder();
        for (String className : classNames) {
            benchmarkCode.append(createTimeMethod(className));
        }

        String caliperClass = "public class " + caliperClassName + " extends SimpleBenchmark";
        return caliperImports + otherImports + "\n" + randomCode.toString() +
                 "\n" + caliperClass +  "{\n" + benchmarkCode.toString() + "}\n";
    }

    /**
     * Convenience method that actually creates the file with all code necessary (including
     * import statements) to have a runnable caliper benchmarking, which evaluates randomly
     * generated code
     * @param dirName directory name to take random code files from, and to save down the caliper
     *                files
     * @param ending endings to be matched (["none", "naive"]),
     * @param caliperClassName name to use as file and class name for benchmarking class
     */
    private static void createBenchmarkFile(String dirName, String ending, String caliperClassName) {
        File[] files = getFiles(dirName, ending);
        if (files != null && files.length > 0) {
            System.out.println("Creating caliper benchmark with " + files.length + " tests");
            String code = createCaliperCode(caliperClassName, files);
            try {
                File file = new File(dirName + File.separator + caliperClassName + ".java");
                Writer writer = new BufferedWriter(new FileWriter(file));
                writer.write(code);
                writer.close();
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    private static void help() {
        System.out.println("usage: <directory-path> <caliper-class-name-prefix>");
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            help();
            System.exit(1);
        }
        String dirName = args[0];
        String caliperName = args[1];
        System.out.println("==> No instrumentation benchmarks");
        createBenchmarkFile(dirName, "none.java", caliperName + "_none");
        System.out.println("==> Naive instrumentation benchmarks");
        createBenchmarkFile(dirName, "naive.java", caliperName + "_naive");
    }



}
