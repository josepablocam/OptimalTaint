package analyzer

/**
 * Utilities for analyzer
 */
object Util {

  // internal counter to generate unique identifiers
  private var COUNTER = 0

  /**
   * Generate a unique identifier
   * @return unique identifier
   */
  def uniqueId() = {
    COUNTER = COUNTER + 1
    COUNTER
  }

  /**
   * Simplify commands (empty sequence -> skip, sequence of 1 -> extract etc)
   * @param com command
   * @return simplified command
   */
  def simplify(com: Com): Com = com match {
    case Seq(x :: Nil) => x
    case Seq(Nil) => Skip
    case _ => com
  }

  /**
   * Extract the set of variables used in a given expression
   * @param exp expression to be analyzed
   * @return set of variable names used
   */
  private [analyzer] def variablesUsed(exp: AExp): Set[String] = exp match {
    case AConst(_) => Set()
    case SConst(_) => Set()
    case IVar(v) => Set(v)
    case AOp(e1, e2) => variablesUsed(e1) ++ variablesUsed(e2)
    case Call(es) => es.foldLeft(Set[String]())((x,y) => x ++ variablesUsed(y))
    case _ => throw new UnsupportedOperationException("not yet implemented")
  }
}
