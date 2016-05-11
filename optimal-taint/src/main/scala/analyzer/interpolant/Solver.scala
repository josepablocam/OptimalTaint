package analyzer.interpolant

import analyzer.prog.Trace
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool
import de.uni_freiburg.informatik.ultimate.logic.{Annotation, Logics, Script, Term}
import de.uni_freiburg.informatik.ultimate.smtinterpol.smtlib2.SMTInterpol


/**
 * A wrapper around an SMT solver to calculate an interpolant for our traces. Roughly follows the
 * example in
 * https://ultimate.informatik.uni-freiburg.de/smtinterpol/InterpolationUsageStub.java
 */
object Solver {
  // prefix branching conditions (which are integers) with a letter
  val FUN_PREFIX = "B"
  // names for the disjunction of conjoined branch conditions
  // P -> tainted, N -> not-tainted sets
  val PHI_P = "phiP"
  val PHI_N = "phiN"

  /**
   * Initialize a "script", which is the SMTInterpol solver, with all necessary options
   * @return SMTInterpol script
   */
  def initScript(): Script = {
    val script = new SMTInterpol()
    // needed for interpolant generation
    script.setOption(":produce-interpolants", true)
    // silence log messages
    script.setOption(":print-success", false)
    script.setOption(":verbosity", 3)
    // we can have the simplest logic for our purposes
    script.setLogic(Logics.QF_LIA)
    script
  }

  /**
   * Declare branch conditions for solver (need to name these variables)
   * @param script
   * @param bs
   */
  def declareBranchConditions(script: Script, bs: Set[Int]): Unit = {
    val funs = bs.map(Math.abs)
    funs.foreach {
      x => script.declareFun(FUN_PREFIX + x, Array(), script.sort("Bool"))
    }
  }

  /**
   * Generate an SMTInterpol term from a trace (i.e. conjoin path condition terms)
   * @param script
   * @param trace
   * @return
   */
  def termFromTrace(script: Script, trace: Trace): Term = {
    val terms = trace.conds.map { x =>
      if (x > 0) script.term(FUN_PREFIX + x) else script.term("not", script.term(FUN_PREFIX + Math.abs(x)))
    }
    if (terms.length > 1) script.term("and", terms:_*) else terms.head
  }

  /**
   * Generate SMTInterpol annotated term that is the disjunction of the traces for a given set.
   * We must annotate to be able to interpolate between terms.
   * @param script
   * @param name used to annotate the term
   * @param bs
   * @return
   */
  def definePhi(script: Script, name: String, bs: Set[Trace]): Term = {
    val clauses = bs.map(trace => termFromTrace(script, trace)).toList
    val phi = if (clauses.length > 1) script.term("or", clauses:_*) else clauses.head
    script.annotate(phi, new Annotation(":named", name))
  }

  // Wrappers for the relevant sets of traces
  def definePhiP(script: Script, bs: Set[Trace]): Term = definePhi(script, PHI_P, bs)
  def definePhiN(script: Script, bs: Set[Trace]): Term = definePhi(script, PHI_N, bs)

  /**
   * Get an interpolant between tainted and non-tainted trace sets. The solver returns a term
   * that has been simplified internally, but will likely require further refinement to be in
   * "intuitive form"
   * @param tainted set of tainted traces
   * @param notTainted set of non-tainted traces
   * @return an interpolant between the two sets
   */
  def getInterpolant(tainted: Set[Trace], notTainted: Set[Trace]): Term = {
    val script = initScript()
    // all branch conditions as variable names
    val branches = tainted.flatMap(_.conds) ++ notTainted.flatMap(_.conds)
    // declare symbolic names for each branch condition
    declareBranchConditions(script, branches.map(Math.abs))
    // declare both phis (which should lead to UNSAT)

    script.push(1)
    script.assertTerm(definePhiP(script, tainted))
    script.assertTerm(definePhiN(script, notTainted))
    val status = script.checkSat()
    assert(status == LBool.UNSAT, "can only interpolate if formula unsat")
    val interpolants = script.getInterpolants(Array(script.term(PHI_P), script.term(PHI_N)))
    script.pop(1)

    assert(interpolants.length == 1, "should only have 1 interpolant")
    // we're not using sequences of interpolants, just a single one
    val interpolant = interpolants.head
    // now we'd like to simplify our interpolant as much as possible with the solver
    script.simplify(interpolant)
  }


}
