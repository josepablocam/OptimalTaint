package com.github.OptimalTaint.analysis.examples;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;

import com.ibm.wala.cfg.Util;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.viz.PDFViewUtil;


/**
 * This examples shows how to:
 * 1 - Add source file data
 * 2 - Construct a control flow graph of a given method
 * 3 - Collect trace conditions
 */
public class CollectTraceConditions {
    /**
     * Simple class to hold both class hierarchy and IR results
     */
    static class ClassHierarchyAndIR {
        IR ir = null;
        ClassHierarchy cha = null;
        public ClassHierarchyAndIR(ClassHierarchy cha, IR ir) {
            this.cha = cha;
            this.ir = ir;
        }
    }

    /**
     * Validate that we managed to add source file for scope
     * @param cha class hierarchy (as constructed by wala)
     * @param progName program name (without extension)
     */
    public static void validateAddedSource(ClassHierarchy cha, String progName) {
        System.out.println("validating source files");

        for(IClass c : cha) {
            if (c.getName().toString().equals("L" + progName)) {
                String srcName = c.getSourceFileName();
                System.out.println("Found source file: " + srcName);

                // read contents of source file
                try {
                    File srcFile = new File(srcName);
                    BufferedReader reader = new BufferedReader(new FileReader(srcFile));
                    String line;
                    String filePrintSeparator = "-------------------------";
                    System.out.println(filePrintSeparator + ">");
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                    System.out.println("<" + filePrintSeparator);
                    reader.close();
                } catch (IOException e) {
                    System.err.println("Unable to open source file from class hierarchy");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Helper method that performs DFS on SSA control flow graph and collects trace conditions
     * @param g control flow graph
     * @param currNode current node in DFS
     * @param currTrace stack of trace conditions
     * @param traces collection of complete traces' conditions
     */
    public static void dfsTraceConditions(
            SSACFG g,
            ISSABasicBlock currNode,
            Stack<Integer> currTrace,
            List<List<Integer>> traces) {

        // done processing current trace so add copy of conditions
        if (currNode.isExitBlock()) {
            List<Integer> completeTrace = new ArrayList<Integer>(currTrace);
            traces.add(completeTrace);
        } else {
            if (com.ibm.wala.cfg.Util.endsWithConditionalBranch(g, currNode)) {
                // True branch
                currTrace.push(currNode.getNumber());
                ISSABasicBlock trueNode = Util.getTakenSuccessor(g, currNode);
                dfsTraceConditions(g, trueNode, currTrace, traces);
                currTrace.pop();
                // False branch
                ISSABasicBlock falseNode = Util.getNotTakenSuccessor(g, currNode);
                // negate condition
                currTrace.push(-1 * currNode.getNumber());
                dfsTraceConditions(g, falseNode, currTrace, traces);
                currTrace.pop();
            } else {
                // explore next node
                ISSABasicBlock nextNode = g.getSuccNodes(currNode).next();
                dfsTraceConditions(g, nextNode, currTrace, traces);
            }
        }
    }

    /**
     * Collect conditions along each trace for a given IR
     * @param ir representation of CFG (from wala)
     * @return list of branch conditions for each trace
     */
    public static List<List<Integer>> getTraceConditions(IR ir) {
        Stack<Integer> currTrace = new Stack<Integer>();
        List<List<Integer>> traceConditions = new ArrayList<List<Integer>>();
        SSACFG g = ir.getControlFlowGraph();
        ISSABasicBlock root = g.getSuccNodes(g.entry()).next();
        dfsTraceConditions(g, root, currTrace, traceConditions);
        return traceConditions;
    }


    /**
     * Create IR of a method in a given program
     * @param prog program name (without .class or .java extension)
     * @param methodSig method signature (as shown by javap -s)
     * @return IR
     */
    public static ClassHierarchyAndIR buildChaAndIR(String prog, String methodSig) throws IOException, WalaException {
        String className = prog + ".class";
        String srcName = prog + ".java";

        // Create files
        File srcFile = new File(srcName);

        // create analysis scope
        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(className, (new FileProvider())
                .getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
        // add source file
        scope.addSourceFileToScope(scope.getApplicationLoader(), srcFile, srcName);

        // Build a class hierarchy representing all classes to analyze.  This step will read the class
        // files and organize them into a tree.
        ClassHierarchy cha = ClassHierarchy.make(scope);

        // Create a name representing the method whose IR we will visualize
        MethodReference mr = StringStuff.makeMethodReference(methodSig);

        // Resolve the method name into the IMethod, the canonical representation of the method information.
        IMethod m = cha.resolveMethod(mr);
        if (m == null) {
            Assertions.UNREACHABLE("could not resolve " + mr);
        }

        // Set up options which govern analysis choices.
        AnalysisOptions options = new AnalysisOptions();

        // Create an object which caches IRs and related information, reconstructing them lazily on demand.
        AnalysisCache cache = new AnalysisCache();

        // Build the IR and cache it.
        IR ir =  cache.getSSACache().findOrCreateIR(m, Everywhere.EVERYWHERE, options.getSSAOptions());
        return new ClassHierarchyAndIR(cha, ir);
    }

    /**
     * Add source file, create IR CFG and display it
     * @param prog program name (without extension name)
     * @param methodSig method signature (as output by javap -s)
     * @throws IOException
     * @throws WalaException
     */
    public static void analyze(String prog, String methodSig) throws IOException, WalaException {
        ClassHierarchyAndIR results = buildChaAndIR(prog, methodSig);

        // validate that we actually added the source file info
        validateAddedSource(results.cha, prog);

        List<List<Integer>> traceConditions = getTraceConditions(results.ir);

        // print out trace conditions
        for(int i = 0; i < traceConditions.size(); i++) {
            System.out.println("Trace Conditions " + i + ": " + traceConditions.get(i));
        }

        // create visualization of control flow graph IR
        String pdfFile = prog + "_cfg.pdf";
        String dotFile = prog + "_cfg.dot";
        Process viewer = PDFViewUtil.ghostviewIR(
                results.cha,
                results.ir,
                pdfFile,
                dotFile,
                WalaExampleConstants.DOT_EXE,
                WalaExampleConstants.GV_EXE);
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            usage();
            System.exit(1);
        }
        String progName = args[0];
        String methodSig = args[1];

        try {
            analyze(progName, methodSig);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Describe usage
     */
    public static void usage() {
        System.out.println("Usage: <progName no extension> <method-signature>");
        System.out.println("Program should be in same directory as running");
    }


}
