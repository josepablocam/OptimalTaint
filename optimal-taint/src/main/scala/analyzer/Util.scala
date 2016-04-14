package analyzer

object Util {

  private var COUNTER = 0

  def uniqueId() = {
    COUNTER = COUNTER + 1
    COUNTER
  }

  def simplify(com: Com): Com = com match {
    case Seq(x :: Nil) => x
    case Seq(Nil) => Skip
    case _ => com
  }


}
