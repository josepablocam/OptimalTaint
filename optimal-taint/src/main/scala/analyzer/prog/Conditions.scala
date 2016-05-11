package analyzer.prog

import analyzer.{Util, prog}

/**
 * Object to analyze trace conditions. Includes functionality to unwind while-loops
 * into if-statements (replaced with fresh conditions), and collect trace branching conditions
 * partitioned along taint
 */
object Conditions {
  /**
   * Unwind a list of commands to eliminate while loops
   * @param coms
   * @return
   */
  private def unwindLoops0(coms: List[Com]): List[Com] = coms match {
    case (loop @ While(limit, b, c1)) :: xs => {
      // unwind body once
      val unwindBody = unwindLoops(c1)
      // replace any conditions fresh each time though
      (0 until limit).toList.map { x =>
        // make sure we copy over the position from the original while loop
        // so we can find position in source code
        If(BExp(Util.uniqueId()), freshConditions(unwindBody), Skip, Some(x)).setPos(loop.pos)
      } ++ unwindLoops0(xs)
  }
    case v :: xs => v :: unwindLoops0(xs)
    case Nil => Nil
  }

  /**
   * Replace all branching conditions in a command with fresh condition identifiers
   * @param com
   * @return
   */
  private def freshConditions(com: Com): Com = com match {
    case If(_, c1, c2, iter) => If(BExp(Util.uniqueId()), freshConditions(c1), freshConditions(c2), iter).setPos(com.pos)
    case Seq(cs) => Seq(cs.map(freshConditions))
    case While(_, _, _) => throw new UnsupportedOperationException("loops must be unwound prior")
    case _ => com
  }

  /**
   * Unwind all while-loops in a command to be replaced by an appropriate number of if-else statements
   * @param com command to unwind
   * @return loop-less commands
   */
  def unwindLoops(com: Com): Com = com match {
    case v @ Assign(_, _) => v
    case If(b, c1, c2, iter) => If(b, unwindLoops(c1), unwindLoops(c2), iter).setPos(com.pos)
    case v @ Incr(_) => v
    case v @ Return => v
    case v @ While(_, _, _) => Util.simplify(prog.Seq(unwindLoops0(v :: Nil)))
    case Skip => Skip
    case Seq(Nil) => Skip
    case Seq(cs) => Util.simplify(prog.Seq(unwindLoops0(cs)))
    case _ => throw new UnsupportedOperationException("note yet implemented")
  }

  /**
   * DFS helper to collect branch conditions along a trace and update tainting
   * @param com command to explore
   * @param ts set of traces at this given point
   * @return updated set of traces (extended as appropriate)
   */
  private def collectTraces0(com: Com, ts: Set[Trace]): Set[Trace] = com match {
    case v @ Assign(_, _) => ts.map(t => Trace.updateTaint(t, v))
    case If(BExp(b), c1, c2, _) => {
      val takeTrue = ts.map { case Trace(conds, env) => Trace(b :: conds, env)}
      val takeFalse = ts.map { case Trace(conds, env) => Trace(-b :: conds, env)}
      collectTraces0(c1, takeTrue) ++ collectTraces0(c2, takeFalse)
    }
    case Init(_, _) => ts
    case Incr(_) => ts
    case Return => ts
    case While(_, _, _) => throw new UnsupportedOperationException("unwind loops first")
    case Skip => ts
    case Seq(cs) => cs.foldLeft(ts) { (x, y) => collectTraces0(y, x)}
    case _ => throw new UnsupportedOperationException("not yet implemented")
  }

  /**
   * Collect a set of all traces along a program
   * @param com command representing program
   * @param tainted set of variables that are assumed to begin the program as tainted (i.e. sources
   *                of taint)
   * @return set of traces (branching conditions and environment associated with that trace at
   *         end of program)
   */
  def collectTraces(com: Com, tainted: Set[String]): Set[Trace] = {
    val initEnv = tainted.map((_, true)).toMap
    val initTraces = Set(Trace(Nil, initEnv))
    val collected = collectTraces0(com, initTraces)
    collected.map { case Trace(conds, env) => Trace(conds.reverse, env)}
  }

  /**
   * Split traces into tainted and not-tainted based on the status of a particular variable
   * @param ts set of complete traces from program
   * @param query variable to query for status
   * @return tainted and non-tainted sets of traces
   */
  def partitionTraces(ts: Set[Trace], query: String): (Set[Trace], Set[Trace]) = {
    ts.partition { case Trace(_, b) => b.getOrElse(query, false) }
  }

}
