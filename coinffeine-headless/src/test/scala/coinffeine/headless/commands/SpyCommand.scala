package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.shell.Command

class SpyCommand(override val keyword: String) extends Command {
  private var _invocations = Seq.empty[String]

  def invocations = _invocations
  def executed = _invocations.nonEmpty

  override val description = s"description of $keyword"

  override def apply(output: PrintWriter, args: String): Unit =
    synchronized { _invocations :+= args }

  override def toString = keyword
}
