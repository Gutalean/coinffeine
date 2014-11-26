package coinffeine.headless.shell

import java.io.PrintWriter

trait Command {
  val keyword: String
  val description: String
  def usage: String = keyword
  def apply(output: PrintWriter, args: String): Unit
}
