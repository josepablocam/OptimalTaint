import org.scalatest._
import analyzer.prog._
import analyzer.interpolant._

/**
 * Testing our interpolator solver (mainly just a wrap around smt interpol so not much to test)
 */
class SolverTestSuite extends FunSuite {
  test("single branch interpolant") {
    val taintedTrace = Trace(List(1), Map())
    val notTaintedTrace = Trace(List(-1), Map())
    val interpolant = Solver.getInterpolant(Set(taintedTrace), Set(notTaintedTrace))
    val parsedInterpolant = SolverParser.parseInterpolant(interpolant.toString).get
    assert(parsedInterpolant match {
      case Var(_) => true
      case _ => false
    }, "should be just a single branch condition")
  }

  test("multiple branch interpolant") {
    val branches = 1 to 5
    // positives provide taint, so any trace with at least one positive branch is tainted
    val paths = branches.foldLeft(Set[List[Int]]()) { (prev, b) =>
      if (prev.isEmpty) Set(List(b), List(-b)) else prev.map(x => b :: x) ++ prev.map(x => -b :: x)
    }

    val traces = paths.map(p => Trace(p, Map()))
    val (taintedTraces, notTaintedTraces) = traces.partition(_.conds.exists(_ > 0))

    val interpolant = Solver.getInterpolant(taintedTraces, notTaintedTraces)
    val parsedInterpolant = SolverParser.parseInterpolant(interpolant.toString).get

    assert(parsedInterpolant match {
      case Or(ls) => ls.toSet === Set("B1", "B2", "B3", "B4", "B5").map(Var)
      case _ => false
    }, "should be disjunction of true branches")
  }




}
