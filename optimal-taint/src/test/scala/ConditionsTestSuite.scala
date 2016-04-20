import org.scalatest._

import analyzer.{Conditions, parse, Util, AST, If, Seq}


class ConditionsTestSuite extends FunSuite with BeforeAndAfterEach {
  override def beforeEach(): Unit = {
    Util.resetUniqueId()
  }


  val ignoreBexpAndIter: (AST, AST) => Boolean = {
    case (If(b1, c1, c2, _), If(b2, c3, c4, _)) =>
      Util.equalsModulo(c1, c3, ignoreBexpAndIter) && Util.equalsModulo(c2, c4, ignoreBexpAndIter)
    case _ => false
  }

  test("unwinding simple while") {
    val loop = parse.parseAll(parse.com, "while (x < 2) { y = 10; }").get
    val ifStr = "if (x < 2) { y = 10; } else {}"
    val ifStmt = parse.parseAll(parse.com, ifStr).get
    val ifVer= Seq(ifStmt :: ifStmt :: Nil)
    val unwoundLoop = Conditions.unwindLoops(loop)

    assert(Util.equalsModulo(ifVer, unwoundLoop, ignoreBexpAndIter), "should be equivalent modulo opaque branching id")

    // check that positions carried over (not checked by equality)
    (ifVer, unwoundLoop) match {
      case (Seq(ls1), Seq(ls2)) =>
        assert(ls1.zip(ls2).forall { case (x, y) =>
          (x.pos.line, x.pos.column) === (y.pos.line, y.pos.column)
        }, "positions should carry over")
      case _ => assert(false, "bad unwinding structure")
    }
  }

  test("unwinding nested while") {
    // maintain odd formating of string below, so that we can easily check position for this
    // contrived test case
    val loopStr = """
    while (x < 2) {
    while (x < 1) {
    y = 10;
    }
    }
    """
    val ifStr = """
    if (x < 2) {
    if (x < 1) {
    y = 10;
    } else {}
    } else {}
    """

    val loop = parse.parseAll(parse.com, loopStr).get
    val ifStmt = parse.parseAll(parse.com, ifStr).get
    val ifVer = Seq(ifStmt :: ifStmt :: Nil)
    val unwoundLoop = Conditions.unwindLoops(loop)
    assert(Util.equalsModulo(ifVer, unwoundLoop, ignoreBexpAndIter), "should be equivalent modulo opaque branching id")

    // check that positions carried over (not checked by equality)
    (ifVer, unwoundLoop) match {
      case (Seq(ls1), Seq(ls2)) =>
        assert(ls1.zip(ls2).forall { case (x, y) =>
          (x.pos.line, x.pos.column) === (y.pos.line, y.pos.column)
        }, "positions should carry over")
      case _ => assert(false, "bad unwinding structure")
    }

  }

  test("unwinding no loops") {
    val noLoops = parse.parseAll(parse.com, "int x = 10; x++; y = 10; if (y < 10) { y++;} else { x++;}").get
    val unwound = Conditions.unwindLoops(noLoops)
    assert(noLoops === unwound, "no loops to unwind")
  }

  test("eliminating taint with constant assignment") {
    val progStr =
      """
        y = x + 2;
        y = 100;
      """
    val prog = parse.parseAll(parse.com, progStr).get
    val traces = Conditions.collectTraces(prog, Set("x"))
    val (taint, noTaint) = Conditions.partitionTraces(traces, "y")
    assert(taint.isEmpty, "constant assignment should eliminate taint")
  }

  test("eliminating taint with non-tainted expression") {
    val progStr =
      """
        y = x + 2;
        y = z * w;
      """
    val prog = parse.parseAll(parse.com, progStr).get
    val traces = Conditions.collectTraces(prog, Set("x"))
    val (taint, noTaint) = Conditions.partitionTraces(traces, "y")
    assert(taint.isEmpty, "expression with no taint should eliminate taint")
  }

  test("collect trace conditions") {
    val progStr = """
      if (x < 1) {
        y = 10;
        } else {
          // skip
        }
      z = 10;
      if (y > 1) {
        z = 10;
      }
      else {
        if (w > 2) {
        //skip
        } else {
          //skip
        }
      } """
    val prog = parse.parseAll(parse.com, progStr).get
    Util.resetUniqueId()
    val relabeledProg = Util.relabelBranchesDFS(prog)
    val traces = Conditions.collectTraces(relabeledProg, Set())
    val conditions = traces.map(x => x.conds)
    val expected = Set(
      List(1, 2),
      List(1, -2, 3),
      List(1, -2, -3),
      List(-1, 2),
      List(-1, -2, 3),
      List(-1, -2, -3)
    )
    assert(conditions === expected, "missed some conditions")
  }

  test("partition trace conditions") {
    val progStr =
    """
      if (x > 2) {
        y = 100;
      } else {
        y = x + 2;
      }
    """
    val prog = parse.parseAll(parse.com, progStr).get
    val traces = Conditions.collectTraces(prog, Set("x"))
    val (taint, noTaint) = Conditions.partitionTraces(traces, "y")
    assert(taint.map(_.conds) === Set(List(-1)), "assigning tainted expression taints y")
    assert(noTaint.map(_.conds) === Set(List(1)), "assigning constant expression doesn't taint y")
  }
}
