package analyzer.prog

import scala.util.parsing.input.Positional

sealed abstract class AST extends Positional {
    def pretty(): String = print.pretty(this)
  }

// Aexp
sealed abstract class AExp extends AST
case class AConst(n: Int) extends AExp
case class SConst(n: String) extends AExp // for convenience...
case class IVar(x: String) extends AExp
// abstract away way operation... don't really care
case class AOp(v1: AExp, v2: AExp) extends AExp
case class Call(vs: List[AExp]) extends AExp

// Bexp
// abstract away booleans
case class BExp(n: Int) extends AST

// commands
sealed abstract class Com extends AST
case class Assign(v: String, value: AExp) extends Com
// iter is used to count iterations in unwound while-loop (which unwinds into if-statements)
case class If(b: BExp, c1: Com, c2: Com, iter: Option[Int]) extends Com
case class While(limit: Int, b: BExp, c1: Com) extends Com
case class Seq(cs: List[Com]) extends Com
case object Skip extends Com
// java necessities
case class Init(v: String, value: AExp) extends Com
case class Incr(v: String) extends Com
case object Return extends Com

// instrumentation instructions
sealed abstract class InstrumentationInstr extends Com
case class AddTaint(v: String) extends InstrumentationInstr
case class RemoveTaint(v: String) extends InstrumentationInstr
case class InitShadowVar(v: String) extends InstrumentationInstr