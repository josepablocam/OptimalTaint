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
   * Reset the unique identifier counter
   */
  def resetUniqueId() = {
    COUNTER = 0
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

  /**
   * Arbitrary equality on AST nodes
   * @param a1 ast node
   * @param a2 ast node
   * @param modulo function for arbitrary equality on 2 ast nodes
   *               For example, if we want to ignore the numbering of branching conditions
   *               val modulo: (AST, AST) => Boolean = {
   *               case (BExp(_), BExp(_)) => true
   *               case _ => false
   *               }
   * @return equality modulo arbitrary function
   */
  def equalsModulo(a1: AST, a2: AST, modulo: (AST, AST) => Boolean): Boolean = {
    modulo(a1, a2) || ((a1, a2) match {
      case (AConst(n1), AConst(n2)) => n1 == n2
      case (SConst(s1), SConst(s2)) => s1 == s2
      case (IVar(v1), IVar(v2)) => v1 == v2
      case (AOp(e1, e2), AOp(e3, e4)) => equalsModulo(e1, e2, modulo) && equalsModulo(e3, e4, modulo)
      case (Call(ls1), Call(ls2)) => ls1.length == ls2.length && ls1.zip(ls2).forall{case (x, y) => equalsModulo(x, y, modulo)}
      case (BExp(b1), BExp(b2)) => b1 == b2
      case (Assign(x1, v1), Assign(x2, v2)) => x1 == x2 && equalsModulo(v1, v2, modulo)
      case (If(b1, m1, m2, iter1), If(b2, m3, m4, iter2)) =>
        equalsModulo(b1, b2, modulo) && equalsModulo(m1, m3, modulo) && equalsModulo(m2, m4, modulo) && iter1 == iter2
      case (Seq(ls1), Seq(ls2)) => ls1.length == ls2.length && ls1.zip(ls2).forall{case (x, y) => equalsModulo(x, y, modulo)}
      case (Skip, Skip) => true
      case (Init(x1, v1), Init(x2, v2)) => x1 == x2 && equalsModulo(v1, v2, modulo)
      case (Incr(x1), Incr(x2)) => x1 == x2
      case (Return, Return) => true
      case _ => false
    })
  }

  /**
   * Relabels branching conditions in a tree in DFS manner (coincides with intuitive numbering)
   * @param c command to relabel
   * @return relabeled command
   */
  def relabelBranchesDFS(c: Com): Com = c match {
    case If(b, c1, c2, iter) => If(BExp(Util.uniqueId()), relabelBranchesDFS(c1), relabelBranchesDFS(c2), iter)
    case While(limit, b, c1) => While(limit, BExp(Util.uniqueId()), relabelBranchesDFS(c1))
    case Seq(ls) => Seq(ls.map(relabelBranchesDFS))
    case _ => c
  }

}
