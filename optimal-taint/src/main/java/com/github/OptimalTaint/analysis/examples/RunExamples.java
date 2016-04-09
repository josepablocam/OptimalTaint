package com.github.OptimalTaint.analysis.examples;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;

/**
 * Helper class to just run all examples on a simple pre-cooked .java sample
 */
public class RunExamples {
    // Our precooked source file example
    static String EXAMPLE_SRC = "public class Example {\n" +
            "    public static int taint(int val) {\n" +
            "      return val;\n" +
            "    }\n" +
            "  \n" +
            "    public static void main(String[] args) {\n" +
            "        int x = taint(10);\n" +
            "        int y = 100;\n" +
            "        if (x > 0) {\n" +
            "          y = x + 100;\n" +
            "        } else {\n" +
            "          y = 100;\n" +
            "        }\n" +
            "        \n" +
            "        if (y > 100) {\n" +
            "          y = 200;\n" +
            "        } else {\n" +
            "          y = x + 20;\n" +
            "        }\n" +
            "    }\n" +
            "}";


    /**
     * Create source file, if already exists, creates a fresh name to avoid clobbering
     * existing files
     * @return name of program (as might have changed based on existing files)
     */
    public static String createExampleFile() {
        try {
            String progName = "Example";
            String srcName = progName + ".java";
            File srcFile = new File(srcName);
            // check if file already exists, if so append index and ammend (avoid clobbering)
            // existing samples/results
            if (srcFile.exists()) {
                System.out.println("Example file already exists, creating fresh");
                File currDir = new File("./");
                File[] similar = currDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.contains("Example") && name.contains(".java");
                    }
                });
                String oldName = progName;
                progName = progName + "_" + (similar.length + 1);
                srcName = progName + ".java";
                // replace name in source code as well
                EXAMPLE_SRC = EXAMPLE_SRC.replace(oldName, progName);
                srcFile = new File(srcName);
            }


            FileWriter writer = new FileWriter(srcFile);
            writer.write(EXAMPLE_SRC);
            writer.close();
            Process process = Runtime.getRuntime().exec("javac " + srcName);
            process.waitFor();
            if (process.exitValue() != 0) {
                System.err.println("Unable to compile " +srcName);
                System.exit(1);
            }

            return progName;

        } catch (Exception e) {
            System.err.println("Unable to create sample Example.java");
            e.printStackTrace();
            System.exit(1);
            return null;
        }

    }

    public static void main(String[] args) {

        String progName = createExampleFile();
        // trace conditions and SSA CFG
        CollectTraceConditions.main(new String[]{progName, progName + ".main([Ljava/lang/String;)V"});
        // slicing on taint and tainted SDG
        ForwardSliceOnTaint.main(new String[]{progName, "main", "taint"});
    }
}
