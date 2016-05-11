package analyzer

import analyzer.InterpolantParser._

import scala.annotation.tailrec
import scala.sys.process._

abstract class Formula {
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

  def constsUsed(e: Formula): Set[String] = e match {
    case Ite(b, t, f) => constsUsed(b) ++ constsUsed(t) ++ constsUsed(f)
    case And(es) => es.flatMap(constsUsed).toSet
    case Or(es) => es.flatMap(constsUsed).toSet
    case Let(binds, scope) => binds.flatMap{ case (x, y) => constsUsed(y)}.toSet ++ constsUsed(scope)
    case Impl(i, t) => constsUsed(i) ++ constsUsed(t)
    case Not(x) => constsUsed(x)
    case Var(v) => Set(v)
    case Const(b) => Set()
  }

  // z3 commands
  def declareConst(s: String): String = s"(declare-fun $s () Bool)"
  def assertTerm(s: String): String = s"(assert $s)"
  def applyCNF: String = "(apply (then (! simplify :elim-and true) elim-term-ite tseitin-cnf))"
  // simplify, then repeatedly split-clause and simplify sub-clause (or skip when done)
  def applyDNF: String = "(apply (then simplify (repeat (or-else (then split-clause simplify) skip))))"


  // compile commands
  def compileZ3DNFCommands(e: Formula): List[String] = {
    // declare constants used
    val consts = constsUsed(e).map(declareConst).toList
    val asserted = assertTerm(e.pretty)
    val completeCommand = consts ::: asserted :: applyCNF :: applyDNF :: Nil
    completeCommand
  }

  def runZ3DNF(interp: String): Formula = {
    val formula = parseInterpolant(interp).get
    val cleanFormula = removeIte(formula).simplify
    val commands = compileZ3DNFCommands(cleanFormula).mkString(" ")
    val z3 = "z3 -in"
    val input = "echo " + commands
    val results: String = input #| z3 !!

    InterpolantParser.parseGoals(results).get.last.simplify
  }

  def findMinimal(dnfInterp: Formula): List[Set[Int]] = {
    val pathsAsSets = setRep(dnfInterp).distinct
    minimize(pathsAsSets)
  }

  def setRep(f: Formula): List[Set[Int]] = f match {
    case Or(xs) => xs.map {
      case And(ys) => ys.map(formulaToInt).toSet
      case v @ Var(vs) => Set(formulaToInt(v))
      case v @ Not(vs) => Set(formulaToInt(v))
      case _ => throw new UnsupportedOperationException("should be dnf (and issue)")
      }

    case _ => throw new UnsupportedOperationException("should be dnf")
  }

  def formulaToInt(f: Formula): Int = f match {
    case Var(b) => b.drop(1).toInt
    case Not(v) => -1 * formulaToInt(v)
    case _ => throw new UnsupportedOperationException("shouldn't convert other formulas to integer")
  }

  @tailrec
  def minimize(fs: List[Set[Int]]): List[Set[Int]] = {
    val next = minimize0(fs, Nil, Set())
    if (fs == next) {
      removeSuperSets(fs.distinct)
    } else {
      minimize(next)
    }
  }

  def removeSuperSets(a: List[Set[Int]]): List[Set[Int]] = {
    // remove any paths for which we can find a shorter conjunction
    a.filterNot {s =>
      a.exists(other => s != other && other.subsetOf(s))
    }
  }

  def minimize0(fs: List[Set[Int]], acc: List[Set[Int]], resolved: Set[Set[Int]]): List[Set[Int]] = fs match {
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
