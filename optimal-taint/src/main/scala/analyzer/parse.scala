package analyzer

import java.io.{FileReader, File}

import scala.util.parsing.combinator._
import scala.util.parsing.input._


object parse extends JavaTokenParsers {
  protected override val whiteSpace = """(\s|//.*|(?m)/\*(\*(?!/)|[^*])*\*/)+""".r

  val reserved: Set[String] =
    Set("true", "false", "if",
      "if", "else", "while", "class", "return")

  val METHOD_NAME = "run"

  // Java wrapping for our experiments
  def prog: Parser[Com] =
    importStmts ~> classes ^^ {
      case (x :: xs) => x
      case Nil => throw new UnsupportedOperationException("only collects commands in run method")
    }

  def importStmt: Parser[String] =
    "import" ~> """[^\n]+""".r

  def importStmts: Parser[List[String]] = importStmt.*

  def classDef: Parser[List[Com]] =
  "class" ~> ident ~> "{" ~> body <~ "}"

  def classes: Parser[List[Com]] =
    classDef.* ^^ {x => x.flatten}

  def body: Parser[List[Com]] =
    method.* ^^ {x => x.collect{ case Some(c) => c}}

  def method: Parser[Option[Com]] =
    ("public" ~> "static" ~> semType ~> ident <~ ignoreBetweenParens <~ "{") ~ com <~ "}" ^^ {
      case METHOD_NAME ~ com =>  Some(com)
      case id ~ _ =>  None
    }

  def semType: Parser[String] =
  "int" | "void" | "long"

  // Actual core syntaax
  def com: Parser[Com] =
  rep(basicCom) ^^ { cs => Util.simplify(Seq(cs)) }


  def basicCom: Parser[Com] =
    initCom |
    incrCom |
    retCom |
    callCom |
    assignCom |
    whileCom |
    ifCom |
    "{" ~> com <~ "}"

  // for java purposes
  def callCom: Parser[Com] =
    call ~> ";" ^^ {x => Skip}

  def assignCom: Parser[Com] =
    ident ~ ("=" ~> aexp <~ ";") ^^ {case id~exp => Assign(id, exp)}

  def whileCom: Parser[Com] =
    ("while" ~> "(" ~> ident ~> "<" ~> wholeNumber <~ ")") ~ basicCom ^^ { case n~c1 => While(n.toInt, BExp(Util.uniqueId()), c1)}

  def ifCom: Parser[If] =
    "if" ~> ifCond ~> basicCom ~ ("else" ~> basicCom) ^^ {
      case c1~c2 =>
        If(BExp(Util.uniqueId()), c1, c2)
    }

  def ifCond: Parser[String] =
    ignoreBetweenParens ^^ {x => x}

  def ignoreBetweenParens: Parser[String] =
  """\((.*?)\)""".r

  def initCom: Parser[Com] =
    (semType ~> ident <~ "=") ~ aexp <~ ";" ^^ { case id~exp => Init(id, exp)}

  def incrCom: Parser[Com] =
    ident <~"++"  <~ ";" ^^ { id => Incr(id)}

  def retCom: Parser[Com] =
    "return" ~> aexp ~> ";" ^^ { x => Return }

  //parse aexp
  def aexp: Parser[AExp] =
    call ~ aop ~ aexp ^^ { case v1 ~ _ ~ v2 => AOp(v1, v2) } |
      call

  def aop: Parser[String] =
    """[-%*/+%]""".r ^^ { case v => v.toString }

  def call: Parser[AExp] =
    ident ~> rep("." ~> ident) ~> "(" ~> aexp ~ rep("," ~> aexp ) <~ ")" ^^ { case x ~ xs => Call(x :: xs) } |
      ident ~> rep("." ~> ident) ~> "(" ~> ")" ^^ { x => Call(Nil)} |
      litOrVar

  // parse variables and integers
  def litOrVar: Parser[AExp] =
    wholeNumber ^^ { n => AConst(n.toInt) } |
      ident ^^ { n => IVar(n)} |
      stringLiteral ^^ { s => SConst(s.substring( 1, s.length-1 ) )} |
      "(" ~> aexp <~ ")" ^^ {x => x}

  // Utilities
  def fromString(s: String) = parseAll(prog, s)
  def fromFile(file: File) = {
    val reader = new FileReader(file)
    parseAll(prog, StreamReader(reader))
  }
}
