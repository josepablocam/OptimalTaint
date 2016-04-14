package analyzer

object Conditions {
  def unwindLoops0(coms: List[Com]): List[Com] = coms match {
    case While(limit, b, c1) :: xs => {
      // unwind body once
      val unwindBody = unwindLoops(c1)
      // replace any conditions fresh each time though
      (0 until limit).toList.map(x => If(BExp(Util.uniqueId()), freshConditions(unwindBody), Skip)) ++ unwindLoops0(xs)
  }
    case v :: xs => v :: unwindLoops0(xs)
    case Nil => Nil
  }


  def freshConditions(com: Com): Com = com match {
    case If(_, c1, c2) => If(BExp(Util.uniqueId()), freshConditions(c1), freshConditions(c2))
    case Seq(cs) => Seq(cs.map(freshConditions))
    case _ => com
  }

  def unwindLoops(com: Com): Com = com match {
    case v @ Assign(_, _) => v
    case If(b, c1, c2) => If(b, unwindLoops(c1), unwindLoops(c2))
    case v @ Incr(_) => v
    case v @ Return => v
    case v @ While(_, _, _) => Util.simplify(Seq(unwindLoops0(v :: Nil)))
    case Skip => Skip
    case Seq(Nil) => Skip
    case Seq(cs) => Util.simplify(Seq(unwindLoops0(cs)))
    case _ => throw new UnsupportedOperationException("note yet implemented")
  }


  def variablesUsed(exp: AExp): Set[String] = exp match {
    case AConst(_) => Set()
    case SConst(_) => Set()
    case IVar(v) => Set(v)
    case AOp(e1, e2) => variablesUsed(e1) ++ variablesUsed(e2)
    case Call(es) => es.foldLeft(Set[String]())((x,y) => x ++ variablesUsed(y))
    case _ => throw new UnsupportedOperationException("not yet implemented")
  }

  def updateTaint(trace: Trace, com: Com): Trace = com match {
    case Assign(v, e) => {
      val varsUsed = variablesUsed(e)
      val env = trace.env
      val usedTainted = varsUsed.exists(x => env.getOrElse(x, false))
      val currTaint = env.getOrElse(v, false)
      // if our expression used no variables, then taint can be eliminated
      val newEnv = if (varsUsed.isEmpty) env.updated(v, false) else env.updated(v, currTaint || usedTainted)
      Trace(trace.conds, newEnv)
    }
    case _ => trace
  }


  case class Trace(conds: List[Int], env: Map[String, Boolean]) {
    override def toString() = {
      "Conditions: [" + conds.mkString(",") +"]\t" + "Env: " + env
    }
  }

  def collectTraces0(com: Com, ts: Set[Trace]): Set[Trace] = com match {
    case v @ Assign(_, _) => ts.map(t => updateTaint(t, v))
    case If(BExp(b), c1, c2) => {
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

  def collectTraces(com: Com, tainted: Set[String]): Set[Trace] = {
    val initEnv = tainted.map((_, true)).toMap
    val initTraces = Set(Trace(Nil, initEnv))
    val collected = collectTraces0(com, initTraces)
    collected.map { case Trace(conds, env) => Trace(conds.reverse, env)}
  }

  def partitionTraces(ts: Set[Trace], query: String): (Set[Trace], Set[Trace]) = {
    ts.partition { case Trace(_, b) => b.getOrElse(query, false) }
  }

}
