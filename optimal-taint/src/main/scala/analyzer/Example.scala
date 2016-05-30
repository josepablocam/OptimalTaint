package analyzer

import analyzer.interpolant.{Solver, Formula}
import analyzer.prog.{Instrumenter, parse, Conditions}

import scala.io.Source


/**
 * Simple example showing how to use analyzer objects to partition traces for
 * a java program fitting core-synrax
 */
object Example {
  val taintedVars = Set("x")
  val queryVar = "y"

  val hardSimplification =
    """
      class HardSimplification {
      public static long run() {
       int x = 10; // tainted
       int y = 20;

       if (b1) { } else { }

       if (b2) {
         // add taint vs nothing
         if (b3) { y = x; } else { }
         if (b4) { y = x; } else { }
       } else { }

        // add taint vs nothing
        if (b5){ y = x; } else { }
      }
     }
    """

  val easySimplification =
    """
      class EasySimplification {
      public static long run() {
        int x = 10; // tainted
        int y = 20;
        int z = 3;
        if (b1) {
          // add taint vs nothing
          if (b2) { y = x + 2; } else { }
        } else { }

        // remove vs maintain taint
        if (b3) { y = 10; } else { y = y + 3; }
        // add taint vs nothing
        if (b4) { y = x + 2; } else { }
      }
    }
    """

  def run (progStr: String): Unit =  {
    val prog = parse.fromString(progStr) match {
      case parse.Success(p, _) => p
      case parse.NoSuccess(_, _) =>  throw new Exception("Failed to parse")
    }
    val unwound = Conditions.unwindLoops(prog)
    Util.resetUniqueId()
    val cleanUnwound = Util.relabelBranchesDFS(unwound)
    val traces = Conditions.collectTraces(cleanUnwound, taintedVars)
    val (tainted, notTainted) = Conditions.partitionTraces(traces, queryVar)

    println("--->Source")
    println(progStr)

    println("Tainted traces")
    tainted.foreach(println)
    println("-----------------")
    println("Not tainted traces")
    notTainted.foreach(println)

    if (tainted.isEmpty) {
      return // done
    }

    val interpolant = Solver.getInterpolant(tainted, notTainted)
    println("--->Interpolant (simplified by solver)")
    println(interpolant)

    val dnfInterp = Formula.runZ3DNF(interpolant.toString)

    println("----> Interpolant (DNF)")
    println(dnfInterp.pretty)

    val minimalInterp = Formula.findMinimal(dnfInterp)
    println("----> Minimal Interpolant (aka intuitive interpolant)")
    println(minimalInterp)

    val instrumentedSrc = Instrumenter.instrumentSourceCode(progStr, instrs)
    println("----> Sketch instrumentation added")
    println(instrumentedSrc)
    println()
  }

  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      run(easySimplification)
      println()
      println()
      run(hardSimplification)
    } else {
      val progStr = Source.fromFile(args(0)).getLines().toList.mkString("\n")
      println(progStr)
      run(progStr)
    }
  }
}
