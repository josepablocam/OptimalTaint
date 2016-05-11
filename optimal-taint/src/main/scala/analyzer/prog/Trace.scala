package analyzer.prog

import analyzer.Util


/**
 * Trace case class
 * @param conds list of branching conditions along a trace, represented as integers. Positive
 *              literals represent taking the true branch, negative represent taking the else branch
 * @param env a mapping from variable names to taint status
 */
case class Trace(conds: List[Int], env: Map[String, Boolean]) {
  override def toString() = {
    "Conditions: [" + conds.mkString(",") +"]\t" + "Env: " + env
  }
}

/**
 * A simple object with Trace utilities, such as updating a trace's environment for taint
 */
object Trace {
  /**
   * Update a trace's environment to reflect any taint stemming from a command.
   * Assigning an expression that uses a tainted variable to a another variable taints the latter.
   * Assigning an expression that does not use any tainted variables results in eliminating taint
   * (this is of course also true for expression that use no variables).
   * @param trace trace to be updated
   * @param com command which might affect taint status of variables in environment
   * @return updated trace as appropriate
   */
  private[analyzer] def updateTaint(trace: Trace, com: Com): Trace = com match {
    case Assign(v, e) => {
      val varsUsed = Util.variablesUsed(e)
      val env = trace.env
      val usedTainted = varsUsed.exists(x => env.getOrElse(x, false))
      // if our assigned expression used no variables (or none that were tainted)
      // then taint can be eliminated
      val newEnv = env.updated(v, usedTainted)
      Trace(trace.conds, newEnv)
    }
    case _ => trace
  }

}
