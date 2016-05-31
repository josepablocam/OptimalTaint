import analyzer.interpolant._
import org.scalatest.FunSuite



/**
 * Test interpolant minimization
 */
class FormulaTestSuite extends FunSuite {
  test("remove ite") {
    // simple ite removal
    val formula1 = SolverParser.parseInterpolant("(ite (b1) (and b2 b3) (or b4 b5))").get
    val noIte = Formula.removeIte(formula1)
    val expected = SolverParser.parseInterpolant("(or (and b1 (and b2 b3)) (and (not b1) (or b4 b5)))").get
    assert(noIte === expected)

    // no ite, idempotent
    val formula2 = SolverParser.parseInterpolant("(and b1 (not b2))").get
    val noIte2 = Formula.removeIte(formula2)
    assert(formula2 === noIte2, "should yield same formula if no ite present")

    // nested ite
    val formula3 = SolverParser.parseInterpolant(
      """
      (ite (ite b2 b3 b4) b5 (not b7))
      """).get
    val noIte3 = Formula.removeIte(formula3)
    val expected3 = SolverParser.parseInterpolant(
      """
        (or
          (and
            ((or (and b2 b3) (and (not b2) b4)))
            b5
          )
          (and
            (not (or (and b2 b3) (and (not b2) b4)))
            (not b7)
          )
        )
      """).get
    assert(noIte3 === expected3, "nested ite statements should be removed as well")
  }

  test("removeSuperSets") {
    val s1 = List(Set(1), Set(2), Set(2, 3), Set(1, 2, 3))
    val result1 = Formula.removeSuperSets(s1).toSet
    val expected1 = Set(Set(1), Set(2))
    assert(result1 === expected1, "should remove paths that imply others in list")

    val s2 = List(Set(1), Set(2))
    val result2 = Formula.removeSuperSets(s2).toSet
    val expected2 = Set(Set(1), Set(2))
    assert(result2 === expected2, "shouldn't remove any if none implied")
  }


  test("resolutionOp") {
    val s1 = Set(1, -2)
    val s2 = Set(1, 2)
    val result1 = Formula.resolutionOp(s1, s2)
    val expected = Some(Set(1), Set(s1, s2))
    assert(result1 === expected, "should resolve")

    val s3 = Set(1, 3)
    val result2 = Formula.resolutionOp(s1, s3)
    assert(result2 === None, "shouldn't resolve")
  }

  test("fixed-point resolution op application + super set elimination (minimize)") {
    val s1 = List(Set(1, -2), Set(1, 2))
    val result1 = Formula.minimize(s1)
    assert(result1 === List(Set(1)), "single application resop")

    val s2 = List(Set(1, 2, 3), Set(1, -2, 3), Set(1, -3))
    // (1, 2, 3) + (1, -2, 3) -> (1, 3) + (1, -3) -> (1)
    val result2 = Formula.minimize(s2)
    assert(result2 === List(Set(1)), "2 applications of resop")

    val s3 = List(Set(1, 2, 3, 4), Set(1, -2, 3, 4), Set(1, 3, -4), Set(-1, 3), Set(1, -3))
    // (1, 2, 3, 4) + (1, -2, 3, 4) -> (1, 3, 4) + (1, 3, -4) -> (1, 3) + (-1, 3) -> (3)
    //                                                           (1, 3) + (1, -3) -> (1)
    val result3 = Formula.minimize(s3).toSet
    assert(result3 === Set(Set(3), Set(1)), "3 applications of resop")


    val s4 = Set(1, 5, 6) :: s3
    val result4 = Formula.minimize(s4).toSet
    assert(result4 === Set(Set(3), Set(1)), "should remove supersets after fixed point")

    val s5 = List(Set(1, 2, 3), Set(1, 2), Set(1, -2))
    val result5 = Formula.minimize(s5)
    assert(result5 === List(Set(1)), "should remove supersets after fixed point")
  }

  test("dnf-rewrite") {
    val interp1 = "(or b1 b2 b3)"
    val dnf1 = Formula.runZ3DNF(interp1)
    val expected1 = SolverParser.parseInterpolant(interp1).get
    assert(dnf1 === expected1, "already dnf")

    val interp2 = "(and (or b1 b2) b3)"
    val dnf2 = Formula.runZ3DNF(interp2)
    val expected2 = SolverParser.parseInterpolant("(or (and b1 b3) (and b2 b3))").get
    assert(dnf2 === expected2, "trivial dnf (distributive property)")

    val interp3 = "(let ((x!1 (and b1 b3))) (or x!1 (and b2 b3)))"
    val dnf3 = Formula.runZ3DNF(interp3)
    assert(dnf3 === expected2, "trivial dnf (distributive property) with trivial let")

    val interp4 ="""
      (let
        ((x!1 (not b3)))
          (ite x!1 b4 b5)
      ) """
    SolverParser.parseInterpolant(interp4)
    val dnf4 = Formula.runZ3DNF(interp4)
    val expected = SolverParser.parseInterpolant("(or (and (not b3) b4) (and b3 b5))").get
    assert(dnf4 === expected, "ite + let, should remove double negation in simplification step")
  }

  test("full pipeline") {
    // already in dnf for simplicity
    val interp1 =
      """
      (or
        (and b1 b2 b3 b4)
        (and b1 (not b2) b3 b4)
        (and b1 b3 (not b4))
        (and (not b1) b3)
        (and b1 (not b3))
      )
      """
    val dnf1 = Formula.runZ3DNF(interp1)
    val minimal1 = Formula.findMinimal(dnf1).toSet
    val expected1 = Set(Set(1), Set(3))
    assert(minimal1 === expected1)

    // note that an actual interpolant wouldn't occur like this, but just for robustness testing
    val interp2 = "(or b2 (not b2))"
    val dnf2 = Formula.runZ3DNF(interp2)
    assert(dnf2 === Const(true), "trivially sat")

    val interp3 = "(and b2 (not b2))"
    val dnf3 = Formula.runZ3DNF(interp3)
    assert(dnf3 === Const(false), "trivially unsat")

    val interp4 =
      """
      (or
        b5
        (and b4 b3 b2)
        (and b2 b1)
        b1
      )
      """
    val dnf4 = Formula.runZ3DNF(interp4)
    val minimal4 = Formula.findMinimal(dnf4).toSet
    val expected4 = Set(Set(5), Set(1), Set(4, 3, 2))
    assert(minimal4 === expected4)
  }
}
