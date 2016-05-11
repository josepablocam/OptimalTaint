package analyzer.interpolant

import scala.annotation.tailrec
import scala.sys.process._
import SolverParser._

/**
 * We use a this class as an internal representation of an interpolant (as a logical formula).
 * We only have the most basic structure needed to be able to manipulate the interpolant and find
 * the intuitive version.
 */
abstract class Formula {
  // Pretty printing formula into SMTLIB2 format
  def pretty: String = this match {
    case Ite(b, t, f) => s"(ite ${b.pretty} ${t.pretty} ${f.pretty})"
    case And(es) => s"(and ${es.map(_.pretty).mkString(" ")})"
    case Or(es) => s"(or ${es.map(_.pretty).mkString(" ")})"
    case Let(binds, scope) => s"(let (${binds.map{ case (x, y) => s"($x ${y.pretty})"}.mkString(" ")}) ${scope.pretty})"
    case Impl(i, t) => s"(=> ${i.pretty} ${t.pretty})"
    case Not(x) => s"(not ${x.pretty})"
    case Var(v) => v
    case Const(b) => b.toString
  }

  /**
   * Trivial simplifier, but helps remove unnecessary structure that otherwise starts to clutter
   * our representation
   * not(not(x)) -> x
   * and(x)/or(x) -> x
   * not(false)/not(true) -> true/false
   * @return
   */
  def simplify: Formula = this match {
    case Ite(b, t, f) => Ite(b.simplify, t.simplify, f.simplify)
    case And(e :: Nil) => e.simplify
    case And(xs) => And(xs.map(_.simplify))
    case Or(e :: Nil) => e.simplify
    case Or(xs) => Or(xs.map(_.simplify))
    case Let(binds, scope) => Let(binds.map { case(x, y) => (x, y.simplify)}, scope.simplify)
    case Impl(i, t) => Impl(i.simplify, t.simplify)
    case Not(Not(f)) => f.simplify
    case Not(Const(b)) => Const(!b)
    case _ => this
  }
}

case class Not(e: Formula) extends Formula
case class Var(v: String) extends Formula
case class Const(b: Boolean) extends Formula
case class And(es: List[Formula]) extends Formula
case class Or(es: List[Formula]) extends Formula
case class Impl(i: Formula, t: Formula) extends Formula
case class Ite(c: Formula, t: Formula, f: Formula) extends Formula
case class Let(binds: List[(String, Formula)], scope: Formula) extends Formula


object Formula {
  /**
   * Replace ite constructs with basic expression.
   * (ite b t f) => (or (and b t) (and (not b) f))
   * This allows us to appropriately convert to CNF/DNF
   * @param e formula expression
   * @return formula expression without ite
   */
  def removeIte(e: Formula): Formula = e match {
    case Ite(b, t, f) => removeIte(Or(And(b :: t :: Nil) :: And(Not(b) :: f :: Nil) :: Nil))
    case And(es) => And(es.map(removeIte))
    case Or(es) => Or(es.map(removeIte))
    case Let(binds, scope) => Let(binds.map{ case (x, y) => (x, removeIte(y))}, removeIte(scope))
    case Impl(i, t) => Impl(removeIte(i), removeIte(t))
    case Not(x) => Not(removeIte(x))
    case Var(v) => Var(v)
    case Const(b) => Const(b)
  }

  /**
   * Get set of variables used in a formula (needed to declare these for solver)
   * @param e
   * @return
   */
  def varsUsed(e: Formula): Set[String] = e match {
    case Ite(b, t, f) => varsUsed(b) ++ varsUsed(t) ++ varsUsed(f)
    case And(es) => es.flatMap(varsUsed).toSet
    case Or(es) => es.flatMap(varsUsed).toSet
    case Let(binds, scope) => binds.flatMap{ case (x, y) => varsUsed(y)}.toSet ++ varsUsed(scope)
    case Impl(i, t) => varsUsed(i) ++ varsUsed(t)
    case Not(x) => varsUsed(x)
    case Var(v) => Set(v)
    case Const(b) => Set()
  }

  // Produce z3 commands
  // Declare a const (declaring a function without arguments)
  def declareConst(s: String): String = s"(declare-fun $s () Bool)"
  // Wrap a term in an assertiong
  def assertTerm(s: String): String = s"(assert $s)"
  // Apply z3 CNF conversion
  def applyCNF: String = "(apply (then (! simplify :elim-and true) elim-term-ite tseitin-cnf))"
  // simplify, then repeatedly split-clause and simplify sub-clause (or skip when done)
  def applyDNF: String = "(apply (then simplify (repeat (or-else (then split-clause simplify) skip))))"

  /**
   * Combine all necessary commands for z3 to take a formula representing an interpolant to
   * intuitive form
   * @param e
   * @return
   */
  def combineZ3DNFCommands(e: Formula): List[String] = {
    // declare constants used
    val consts = varsUsed(e).map(declareConst).toList
    val asserted = assertTerm(e.pretty)
    val completeCommand = consts ::: asserted :: applyCNF :: applyDNF :: Nil
    completeCommand
  }

  /**
   * Execute Z3 to calculate DNF form of an interpolant. Assumes Z3 is directly executable
   * by calling z3. Commands are given via stdin, and results are parsed from stdout.
   * @param interpStr string form of interpolant
   * @return formula representing a DNF form of the interpolant
   */
  def runZ3DNF(interpStr: String): Formula = {
    val formula = parseInterpolant(interpStr).get
    val cleanFormula = removeIte(formula).simplify
    val commands = combineZ3DNFCommands(cleanFormula).mkString(" ")
    val z3 = "z3 -in"
    val input = "echo " + commands
    val results: String = input #| z3 !!

    SolverParser.parseGoals(results).get.last.simplify
  }

  /**
   * Find the minimal version of a DNF interpolant by applying resolution and eliminating superset
   * paths.
   * @param dnfInterp interpolant in DNF form (not being in DNF can yield exceptions)
   * @return
   */
  def findMinimal(dnfInterp: Formula): List[Set[Int]] = {
    val pathsAsSets = pathsToSets(dnfInterp).distinct
    minimize(pathsAsSets)
  }

  /**
   * Convert paths in a DNF interpolant to sets of integers, easier to use for minimization
   * @param f DNF interpolant formula
   * @return
   */
  def pathsToSets(f: Formula): List[Set[Int]] = f match {
    case Or(xs) => xs.map {
      case And(ys) => ys.map(literalToInt).toSet
      case v @ Var(vs) => Set(literalToInt(v))
      case v @ Not(vs) => Set(literalToInt(v))
      case _ => throw new UnsupportedOperationException("should be dnf (and issue)")
      }
    case _ => throw new UnsupportedOperationException("should be dnf")
  }

  /**
   * Convert a formula literal (i.e. Var(x) or Not(Var(x))) to an integer. Drops any leading
   * non-numeric chars. Negated literals are represented by negative integers.
   * @param f
   * @return
   */
  def literalToInt(f: Formula): Int = f match {
    case Var(b) => b.dropWhile(c => !Character.isDigit(c)).toInt
    case Not(v) => -1 * literalToInt(v)
    case _ => throw new UnsupportedOperationException("shouldn't convert other formulas to integer")
  }

  /**
   * Minimize an interpolant (in set-representation) by repeatedly applying resolution operation
   * and eliminating super-sets
   * @param fs interpolant
   * @return intuitive interpolant
   */
  @tailrec
  def minimize(fs: List[Set[Int]]): List[Set[Int]] = {
    val next = minimize0(fs, Nil, Set())
    if (fs == next) {
      removeSuperSets(fs.distinct)
    } else {
      minimize(next)
    }
  }

  /**
   * Iterative application of the resolution operator between possible paths in interpolant
   * @param fs list of paths in interpolant
   * @param acc accumulated list of resolved paths (if two paths resolve, the resolved path
   *            is placed here, if a path resolves with none, it is placed here as well)
   * @param resolved paths that have been successfully yielded a resolved path, we use this to
   *                 avoid adding back paths that have already been trimmed down
   * @return
   */
  private def minimize0(fs: List[Set[Int]], acc: List[Set[Int]], resolved: Set[Set[Int]])
    : List[Set[Int]] = fs match {
    case Nil => acc.reverse
    case x :: xs =>
      val newResolutions = xs.flatMap(c => resolutionOp(x, c))
      // result of resultion operations
      val resolutions = newResolutions.map(_._1)
      // terms used for resolution (shouldn't be added to acc, as we have a smaller clause)
      val used = newResolutions.flatMap(_._2).toSet
      val newResolved = resolved ++ used
      minimize0(xs, if (newResolved.contains(x)) resolutions ::: acc else x :: resolutions ::: acc, newResolved)
  }

  /**
   * Remove supersets
   * Given sets S and S', if S' is a subset of S and S != S', S is a superset and should
   * be removed from our collection.
   * @param paths Paths in an interpolant (represented as sets of integers)
   * @return
   */
  def removeSuperSets(paths: List[Set[Int]]): List[Set[Int]] = {
    // remove any paths for which we can find a shorter conjunction
    paths.filterNot {p =>
      paths.exists(other => p != other && other.subsetOf(p))
    }
  }

  /**
   * Resolution operation between 2 sets of integers (representing branch conditions)
   * Given S and S', S and S' can be resolved if they differen only in 1 literal, such that
   * l in S and not(l) in S'. If this is the case, they resolve to S \ l. If this is not the case,
   * they don't resolve to anything.
   * @param f1
   * @param f2
   * @return None if resolution is not possible, otherwise Some(resolved)
   */
  def resolutionOp(f1: Set[Int], f2: Set[Int]): Option[(Set[Int], Set[Set[Int]])] = {
    if (f1.size != f2.size) {
      None
    } else {
      val diff = f1.diff(f2).union(f2.diff(f1))
      if (diff.size != 2) {
        None
      } else if (diff.contains(-diff.head)) {
        Some(f1.intersect(f2), Set(f1, f2))
      } else {
        None
      }
    }
  }

}
