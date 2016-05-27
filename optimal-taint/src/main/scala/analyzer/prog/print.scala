package analyzer.prog

import org.kiama.output.PrettyPrinter

import scala.util.parsing.input.NoPosition

/**
 * Print out an abstract representation of our programs
 */
object print extends PrettyPrinter {
  override val defaultIndent = 2


  def reportLoopHeader(a: AST, iter: Int): Doc = {
    val lineStr = a.pos match {
      case NoPosition => "?"
      case v => v.line.toString
    }
    "// loop @ line" <+> lineStr <> ", iter:" <+> iter.toString
  }
  /*
   * Pretty-print expressions in abstract syntax
   */
  def show(e: AST): Doc = e match {
      case AConst(n) => n.toString
      case SConst(s) => s
      case IVar(v) => v
        // abstracted arithmetic operator
      case AOp(e1, e2) => parens(show(e1) <+> " o "  <+> show(e2))
      case BExp(b) => "B_" <> b.toString
      case Assign(x, v) => x <+> ":=" <+> show(v) <> ";"
      case Init(x, v) => "int " <+> x <+> "=" <+> show(v) <> ";"
      case Incr(x) => x <> "++;"
      case Return => "return"
      case Skip => "//skip"
      case Seq(ls) => ls.map(show).reduceRight((x, y) => x <@> y)
      case If(b, c1, c2, None) =>
        "if" <+> parens(show(b)) <+>
          braces(nest(line <> show(c1)) <> line) <+>
           "else" <+> braces(nest(line <> show(c2)) <> line)
      case If(b, c1, c2, Some(n)) => reportLoopHeader(e, n) <@> show(If(b, c1, c2, None))
      case While(limit, b, c1) =>
        "while" <> parens("_ <" <+> limit.toString) <+>
          braces(nest(line <> show(c1)) <> line)
      case AddTaint(v) => v + " := true;" <> line
      case RemoveTaint(v) => v + " := false;" <> line
      case InitShadowVar(v) => "boolean " + v + ":= false;" <> line
      case _ => throw new UnsupportedOperationException("printing not yet implemented")
    }

  def pretty(e: AST): String = pretty(show(e))
}
  
  