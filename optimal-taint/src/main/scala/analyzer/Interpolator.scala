package analyzer

import de.uni_freiburg.informatik.ultimate.logic.Script.LBool
import de.uni_freiburg.informatik.ultimate.logic.{Annotation, Logics, Script, Sort, Term}
import de.uni_freiburg.informatik.ultimate.smtinterpol.smtlib2.SMTInterpol
import de.uni_freiburg.informatik.ultimate.logic.Model


// following example in
// https://ultimate.informatik.uni-freiburg.de/smtinterpol/InterpolationUsageStub.java
object Interpolator {
  val FUN_PREFIX = "B"
  val PHI_P = "phiP"
  val PHI_N = "phiN"

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

  def declareBranchConditions(script: Script, b: Set[Int]): Unit = {
    val funs = b.map(Math.abs)
    funs.foreach {
      x => script.declareFun(FUN_PREFIX + x, Array(), script.sort("Bool"))
    }
  }

  def termFromTrace(script: Script, trace: Trace): Term = {
    val terms = trace.conds.map { x=>
      if (x > 0) script.term(FUN_PREFIX + x) else script.term("not", script.term(FUN_PREFIX + Math.abs(x)))
    }
    if (terms.length > 1) script.term("and", terms:_*) else terms.head
  }

  def definePhi(script: Script, name: String, bs: Set[Trace]): Term = {
    val clauses = bs.map(trace => termFromTrace(script, trace)).toList
    val phi = if (clauses.length > 1) script.term("or", clauses:_*) else clauses.head
    script.annotate(phi, new Annotation(":named", name))
  }

  def definePhiP(script: Script, bs: Set[Trace]): Term = definePhi(script, PHI_P, bs)
  def definePhiN(script: Script, bs: Set[Trace]): Term = definePhi(script, PHI_N, bs)

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

    // now we'd like to simplify our interpolant
    val simpleInterpolant = script.simplify(interpolant)

    script.assertTerm(interpolant)
    script.push(1)

    script.pop(1)

    simpleInterpolant
  }


}
