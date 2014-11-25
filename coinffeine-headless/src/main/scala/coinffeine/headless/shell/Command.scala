package coinffeine.headless.shell

import java.io.PrintWriter

trait Command {
  val keyword: String
  def apply(output: PrintWriter, args: String): Unit
}
