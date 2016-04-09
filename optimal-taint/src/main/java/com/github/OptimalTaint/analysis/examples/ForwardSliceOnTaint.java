package com.github.OptimalTaint.analysis.examples;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.core.tests.slicer.SlicerTest;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.PDFViewUtil;



/**
 * This examples shows how to:
 * 1 - Construct a call graph
 * 2 - Slice statements based on given taint source
 * 3 - Identify SSA instructions associated with tainted statements
 */
public class ForwardSliceOnTaint {
    static final DataDependenceOptions DATA_FLOW_OPTIONS = DataDependenceOptions.FULL;
    static final ControlDependenceOptions CONTROL_FLOW_OPTIONS = ControlDependenceOptions.NONE;


    /**
     * Construct forward slice of a program based on taint
     * @param progName program name (without extension)
     * @param srcCaller name of method where taint method is called
     * @param srcCallee taint method name
     * @return
     */
    public static Graph<Statement> getSlice(String progName, String srcCaller, String srcCallee) {
        try {
            String classFile = progName + ".class";
            String className = "L" + progName;

            AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(classFile, (new FileProvider())
                    .getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));

            // build a class hierarchy, call graph, and system dependence graph
            ClassHierarchy cha = ClassHierarchy.make(scope);

            Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha, className);
            AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

            // To my understanding:
            // 0-1- control flow analysis: context insensitive, 1 step identificatino of possible contents in variables
            CallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(options, new AnalysisCache(), cha, scope);

            CallGraph cg = builder.makeCallGraph(options, null);
            PointerAnalysis pa = builder.getPointerAnalysis();
            // system dependence graph (encodes data and control flow dependencies across the program)
            SDG sdg = new SDG(cg, pa, DATA_FLOW_OPTIONS, CONTROL_FLOW_OPTIONS);

            // find main method (in our examples taint() is only called from main)
            CGNode callerNode = SlicerTest.findMethod(cg, srcCaller);
            // find call to taint() (source of taint in our program)
            Statement s = SlicerTest.findCallTo(callerNode, srcCallee);

            // compute the slice as a collection of statements
            Collection<Statement> slice = null;
            // forward slice (what statements are affected by the return value of a call)
            s = getReturnStatementForCall(s);
            slice = Slicer.computeForwardSlice(s, cg, pa, DATA_FLOW_OPTIONS, CONTROL_FLOW_OPTIONS);

            // dump slice to stdout
            SlicerTest.dumpSlice(slice);

            // create a view of the SDG restricted to nodes in the slice
            return pruneSDG(sdg, slice);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Shamelessly copied from WALA examples
     * return a view of the sdg restricted to the statements in the slice
     */
    public static Graph<Statement> pruneSDG(SDG sdg, final Collection<Statement> slice) {
        CollectionFilter<Statement> f = new CollectionFilter<Statement>(slice) {
            public boolean accepts(Statement o) {
                return slice.contains(o);
            }
        };
        return GraphSlicer.prune(sdg, f);
    }


    /**
     * Collect instructions associated with statements that have been found to be tainted
     * by forward slice
     * @param taintedStmts statements from forward slice
     * @return
     */
    public static List<SSAInstruction> collectForwardInstructions(Graph<Statement> taintedStmts) {
        List<SSAInstruction> taintedInsts = new ArrayList<SSAInstruction>();
        Iterator<Statement> stmtsIter = taintedStmts.iterator();
        String instSeparator = "---------------------------------";
        for(Statement statement = null ; stmtsIter.hasNext(); ) {
            statement = stmtsIter.next();
            Kind kind = statement.getKind();
            // we'll only care about normal and phi statements here
            if (kind == Kind.NORMAL) {
                NormalStatement ns = (NormalStatement) statement;
                SSAInstruction inst = ns.getInstruction();
                taintedInsts.add(inst);
                int index = ns.getInstructionIndex();
                int defines = inst.getDef();
                System.out.println(instSeparator + ">");
                System.out.println("Instruction @" + index);
                if (defines >= 0) {
                    System.out.println("Defines: v" + defines);
                }
                System.out.println(inst.toString());
                System.out.println("<" + instSeparator);
            }

            if (kind == Kind.PHI) {
                PhiStatement ps = (PhiStatement) statement;
                SSAPhiInstruction inst = ps.getPhi();
                taintedInsts.add(inst);
                System.out.println(instSeparator + ">");
                System.out.println("Phi instruction");
                int defines = inst.getDef();
                if (defines >= 0) {
                    System.out.println("Defines: v" + defines);
                }
                System.out.println(inst.toString());
                System.out.println("<" + instSeparator);
            }
        }

        return taintedInsts;

    }

    /**
     * Shamelessly copied from WALA examples
     * If s is a call statement, return the statement representing the normal return from s
     */
    public static Statement getReturnStatementForCall(Statement s) {
        if (s.getKind() == Statement.Kind.NORMAL) {
            NormalStatement n = (NormalStatement) s;
            SSAInstruction st = n.getInstruction();
            if (st instanceof SSAInvokeInstruction) {
                SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) st;
                if (call.getCallSite().getDeclaredTarget().getReturnType().equals(TypeReference.Void)) {
                    throw new IllegalArgumentException("this driver computes forward slices from the return value of calls.\n" + ""
                            + "Method " + call.getCallSite().getDeclaredTarget().getSignature() + " returns void.");
                }
                return new NormalReturnCaller(s.getNode(), n.getInstructionIndex());
            } else {
                return s;
            }
        } else {
            return s;
        }
    }

    /**
     * Construct call graph, forward slice based on taintage, collect and print IR instructions
     * associated with tainted statements.
     * @param progName program name without extensions
     * @param srcCaller name of method where taint method is called
     * @param srcCallee name of taint method
     */
    public static void analyze(String progName, String srcCaller, String srcCallee) {
        try {
            Graph<Statement> slice = getSlice(progName, srcCaller, srcCallee);
            List<SSAInstruction> taintedInstructions = collectForwardInstructions(slice);

            // Visualize the SDG of the slice
            // create a dot representation.
            String pdfFile = progName + "_fwd_slice.pdf";
            String dotFile = progName + "_fwd_slice.dot";
            DotUtil.dotify(slice, null, dotFile, pdfFile, WalaExampleConstants.DOT_EXE);
            // fire off the PDF viewer
            Process viewer = PDFViewUtil.launchPDFView(pdfFile, WalaExampleConstants.GV_EXE);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            usage();
            System.exit(1);
        }
        String progName = args[0];
        String srcCaller = args[1];
        String srcCallee = args[2];
        analyze(progName, srcCaller, srcCallee);
    }

    public static void usage() {
        System.out.println("Usage: <prog-name no extension> <srcCaller method name> <srcCallee method name>");
        System.out.println("srcCaller: Source name of method which calls tainting function");
        System.out.println("srcCallee: Source name of tainting method");
    }
}
