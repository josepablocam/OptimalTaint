package analyzer.prog

import scala.annotation.tailrec

/**
 * Add instrumentation instructions to source code based on a minimal interpolant
 */
object Instrumenter {
  // added to variable name to create shadow variable
  val SHADOW_SUFFIX = "_shadow"

  /**
   * Create shadow variable name by appending suffix
   * @param x variable name
   * @return corresponding shadow variable name
   */
  def shadowVar(x: String): String = x + SHADOW_SUFFIX

  private def whichBranch(where: Int, has: Int, nestedMap: Map[Int, Set[Int]]): Int = {
    if (nestedMap(where).contains(has)) 1 else if (nestedMap(-where).contains(has)) -1 else 0
  }

  /**
   * Obtain a list of instrumentation instructions (annotated with the line number in the original
   * source code)
   * @param prog unwound program, with branch conditions relabeled according to DFS
   *             (Util.relabelBranchesDFS)
   * @param minInterp minimal interpolant in set form
   * @param taintedVars set of tainted variables
   * @param queryVar query variable
   * @return list of instrumentation instructions
   */
  def getInstrumentationInstructions(
    prog: Com,
    minInterp: List[Set[Int]],
    taintedVars: Set[String],
    queryVar: String)
  : List[InstrumentationInstr] = {
    // obtain a map indicating which conditions are nested within others
    val nestedBranches = Conditions.nestedBranchMap(prog)
    // sort branching conditions by absolute value (recall negated conditions are negative)
    // this sorting gives position in DFS of program
    // we follow these as partial paths through the program
    val partialPaths = minInterp.map(_.toList.sortBy(Math.abs))
    // generate instrumentation instructions for each of the partial paths in the minimal interpolant
    val instrs = partialPaths.flatMap(partialPath =>
      instrumentPath(prog, partialPath, nestedBranches, taintedVars, queryVar)
    )
    // first thing we do is initialize the shadow variable
    val initInstr = InitShadowVar(shadowVar(queryVar)).setPos(prog.pos)
    initInstr :: instrs
  }

  /**
   * Generate list of instrumentation instructions by following one partial path from the
   * minimal interpolant
   * @param prog program representation
   * @param path partial path to follow
   * @param nestedMap map indicating which conditions are nested in which other conditions
   * @param taintedVars set of variables with source taint
   * @param queryVar variable to query for taint at end of program
   * @return list of instrumentaton instructions
   */
  def instrumentPath(
    prog: Com,
    path: List[Int],
    nestedMap: Map[Int, Set[Int]],
    taintedVars: Set[String],
    queryVar: String)
  : List[InstrumentationInstr] = {
    // initialize an empty trace
    val initEnv = taintedVars.map((_, true)).toMap
    val initTrace = Trace(Nil, initEnv)
    // follow our partial path, generating instrumentation instructions along the way
    val (_, _, instr) = followPartialPath(prog, path, nestedMap, initTrace, Nil, queryVar)
    instr
  }

  /**
   * Follow the partial path and generate instrumentation instructions as appropriate
   * @param com program commands
   * @param path partial path, consisting of branch conditions
   * @param nestedMap map indicating which conditions are nested in which other conditions
   * @param trace trace (holds taint information etc)
   * @param instrs list of instrumentation instructions
   * @param queryVar variable to query for taint
   * @return
   */
  private def followPartialPath(
    com: Com,
    path: List[Int],
    nestedMap: Map[Int, Set[Int]],
    trace: Trace,
    instrs: List[InstrumentationInstr],
    queryVar: String)
  : (Trace, List[Int], List[InstrumentationInstr]) = (com, path) match {
    // instrumentation instructions pertain to branching points in the program
    case (If(b, c1, c2, _), x :: xs) if b.n == Math.abs(path.head) => {
      // we are done with this branch
      // if xs empty or next condition is not nested within the current condition
      val done = xs.isEmpty || (whichBranch(b.n, xs.head, nestedMap) == 0)
      if (done) {
        // we found a relevant branch for instrumentation
        // so just add an instrumentation instruction according to what happens in the branches
        // and the state of the partial trace thus far
        val (taintBranch, noTaintBranch) = if (x == b.n) (c1, c2) else (c2, c1)
        // get effects of the taint branch by "executing" commands
        val taintTrace = Trace.updateTaint(trace, taintBranch)
        val alreadyTainted = trace.env.getOrElse(queryVar, false)
        val shadow = shadowVar(queryVar)
        val alreadyMarked = trace.env.contains(shadow)
        // generate instrumentation instruction according to state of information
        val newInstr = if (!alreadyTainted) {
          // this is the first place were it becomes tainted in the partial path
          AddTaint(shadow).setPos(taintBranch.pos)
        } else if (alreadyMarked) {
          // we already tainted (alreadyTainted) this through a conditional (alreadyMarked)
          // so the non-taint branch must remove taint
          RemoveTaint(shadow).setPos(noTaintBranch.pos)
        } else {
          // we tainted already (alreadyTainted) through an unconditional command (!alreadyMarked)
          // so the taint branch must add taint shadow
          AddTaint(shadow).setPos(taintBranch.pos)
        }
        // marks shadow var for purposes of distinguishing conditional/unconditional taintage
        val newTrace = taintTrace.copy(env = taintTrace.env.updated(shadow, true))
        (newTrace, xs, newInstr :: instrs)
      } else {
        // we have not yet arrived at our instrumentation point, which is nested inside this
        // conditional
        // follow only the necessary branch
        val inTrueBranch = whichBranch(b.n, xs.head, nestedMap) > 0
        val followBranch = if (inTrueBranch) c1 else c2
        // and we remove the current branching condition from the partial path, as it is now sat
        followPartialPath(followBranch, xs, nestedMap, trace, instrs, queryVar)
      }
    }
    // TODO: another option is to make partial paths include any branching conditions that contain them
    case (If(b, c1, c2, _), x :: xs) if whichBranch(b.n, x, nestedMap) != 0 => {
      val inTrueBranch = whichBranch(b.n, x, nestedMap) > 0
      val followBranch = if (inTrueBranch) c1 else c2
      // don't remove condition from path just yet, as we have not yet arrived at it
      followPartialPath(followBranch, path, nestedMap, trace, instrs, queryVar)
    }
    case (Seq(cs), xs) =>
      cs.foldLeft((trace, path, instrs)) {
        case ((t, p, is), c) =>
          followPartialPath(c, p, nestedMap, t, is, queryVar)
      }
    case (While(_, _, _), _) => throw new UnsupportedOperationException("unwind loops first")
    case _ => (trace, path, instrs)
  }

  /**
   * Add instrumentation to original source code
   * @param progStr source code in string form
   * @param instrs list of instrumentation instructions
   * @return instrumented source code
   */
  def instrumentSourceCode(progStr: String, instrs: List[InstrumentationInstr]): String = {
    // parser line starts at 1 rather than 0
    val lines = progStr.split("\n").toList.zipWithIndex.map(x => (x._1, x._2 + 1))
    // make sure instrumentation instructions are in ascending order
    // as we remove them from our list as we encounter line numbers in source code
    val sortedInstrs = instrs.sortBy(_.pos.line)
    // actually add instructions to original source code
    instrumentSourceCode0(lines, sortedInstrs, Nil).mkString("\n")
  }

  /**
   * Instrument source code by adding instructions to original source code (string)
   * @param codeAndLineNum tuples of line of code along with line number (1-indexed)
   * @param instrs instrumentation instructions
   * @param acc code accumulator
   * @return list of code lines, which can be concatenated with new line chars
   */
  @tailrec
  private def instrumentSourceCode0(
    codeAndLineNum: List[(String, Int)],
    instrs: List[InstrumentationInstr],
    acc: List[String])
  : List[String] = (codeAndLineNum, instrs) match {
    case (l :: ls, x :: xs) if l._2 == x.pos.line => {
      // found the appropriate line in the source code
      // now consume up to the column char that we need
      val col = x.pos.column - 1
      val (lpre, lpost) = l._1.splitAt(col)
      val lsep = if (lpre.trim.isEmpty) "" else " "
      // new line of code has instrumentation added
      val newLine = lpre + lsep + generateInstrumentation(x) + " " + lpost
      instrumentSourceCode0(ls, xs, newLine :: acc)
    }
    case (l :: ls, x :: xs) => instrumentSourceCode0(ls, instrs, l._1 :: acc)
    case (ls, Nil) => (ls.map(_._1) ::: acc).reverse
  }

  /**
   * Generate java code for different types of instrumentation instructions. Affect
   * shadow variables only
   * @param x
   * @return
   */
  private def generateInstrumentation(x: InstrumentationInstr): String = x match {
    case AddTaint(v) => v + " = true;"
    case RemoveTaint(v) => v + " = false;"
    case InitShadowVar(v) => "boolean " + v + " = false;"
  }

}
