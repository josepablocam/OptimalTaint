package analyzer

import java.io.File

/**
 * Simple example showing how to use analyzer objects to partition traces for
 * a java program fitting core-synrax
 */
object Example {
  def main (args: Array[String]) {
    val fileName = args(0)
    val taintedVar = Set(args(1))
    val queryVar = args(2)

    val file = new File(fileName)
    val prog = parse.fromFile(file) match {
      case parse.Success(p, _) => p
      case parse.NoSuccess(_, _) =>  throw new Exception("Failed to parse")
    }
    val unwound = Conditions.unwindLoops(prog)
    val traces = Conditions.collectTraces(unwound, taintedVar)
    val (tainted, notTainted) = Conditions.partitionTraces(traces, queryVar)

    println("Tainted traces")
    tainted.foreach(println)
    println("-----------------")
    println("Not tainted traces")
    notTainted.foreach(println)

    val interpolant = Interpolator.getInterpolant(tainted, notTainted)
    println("--->Interpolant")
    println(interpolant)

  }
}