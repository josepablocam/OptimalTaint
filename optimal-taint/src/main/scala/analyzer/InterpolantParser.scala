package analyzer

import scala.util.parsing.combinator._


/**
 * Parsing interpolants in order to be able to manipulate and find "intuitive" interpolant
 * (i.e. a minimal disjunction of path conditions)
 */
object InterpolantParser extends JavaTokenParsers {
  protected override val whiteSpace = """(\s|//.*|(?m)/\*(\*(?!/)|[^*])*\*/)+""".r

  def formula: Parser[Formula] =
    "(" ~> formula <~ ")" |
    let  |
    ite  |
    andOr |
    impl |
    const |
    id   |
    not


  def let: Parser[Formula] =
    "(" ~> "let" ~> ("(" ~> rep1(varBinding) <~ ")") ~ formula <~ ")" ^^ { case xs ~ f => Let(xs, f)}

  def varBinding: Parser[(String, Formula)] =
    "(" ~> id ~ formula <~ ")" ^^ { case Var(s) ~ f => (s, f) }

  def ite: Parser[Formula] =
    "(" ~> "ite" ~> formula ~ formula ~ formula <~ ")" ^^ { case b ~ t ~ f => Ite(b, t, f) }

  def andOr: Parser[Formula] =
    "(" ~> "and" ~>  rep1(formula) <~ ")" ^^ { xs => And(xs)} |
      "(" ~> "or" ~> rep1(formula) <~ ")" ^^ { xs => Or(xs) }

  def impl: Parser[Formula] =
    "(" ~> "=>" ~> formula ~ formula <~ ")" ^^ { case i ~ t => Impl(i, t) }

  def z3Vars: Parser[String] =
    """[a-zA-Z]+![0-9]+""".r

  def smtInterpolVars: Parser[String] =
    """\.[a-zA-Z]+[a-zA-Z0-9]*""".r

  def id: Parser[Formula] =
    z3Vars ^^ {s => Var(s)} |
      smtInterpolVars ^^ { s => Var(s) } |
      ident ^^ { s =>  Var(s)}

  def not: Parser[Formula] =
    "(" ~> "not" ~> formula <~ ")" ^^ { x => Not(x)}

  def const: Parser[Formula] =
    "true" ^^ {_ => Const(true) } |
    "false" ^^ {_ => Const(false) }

  // goals
  def output: Parser[List[Formula]] =
    rep1(goals)

  def goals: Parser[Formula] =
    "(" ~> "goals" ~> rep1(goal) <~ ")" ^^ { case xs => Or(xs) }

  def goal: Parser[Formula] =
    "(" ~> "goal" ~> rep1(formula) <~ goalInfo <~ ")" ^^ {case xs => And(xs)}

  def goalInfo: Parser[String] =
    ":precision" ~> id ~> ":depth" <~ wholeNumber


  // Utilities
  def parseInterpolant(s: String) = parseAll(formula, s)
  def parseGoals(s: String) = parseAll(output, s)

}



