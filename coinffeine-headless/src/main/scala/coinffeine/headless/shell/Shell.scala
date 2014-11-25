package coinffeine.headless.shell

import java.io.{PrintWriter, InputStream, OutputStream}

import scala.annotation.tailrec

import jline.console.ConsoleReader

class Shell(prompt: Prompt, commands: Seq[Command], input: InputStream, output: OutputStream) {
  import Shell._

  def this(prompt: Prompt, commands: Seq[Command]) = this(prompt, commands, System.in, System.out)

  private val console = new ConsoleReader(input, output)
  private val formatter = new PrintWriter(console.getOutput)
  private val commandsByKeyword: Map[String, Command] = commands.map(c => c.keyword -> c).toMap

  def run(): Unit = {
    interpreterLoop()
    console.shutdown()
  }

  @tailrec
  private def interpreterLoop(): Unit = {
    console.readLine(prompt.value) match {
      case null | CommandPattern("exit", _) => // Exit loop

      case EmptyLinePattern() => interpreterLoop()

      case CommandPattern(keyword, arguments) =>
        commandsByKeyword.get(keyword)
          .fold(reportUnknownCommand(keyword))(_.apply(formatter, arguments))
        interpreterLoop()
    }
  }

  private def reportUnknownCommand(keyword: String): Unit = {
    formatter.format("Unknown command '%s'%n", keyword)
  }
}

object Shell {
  private val CommandPattern = """\s*(\S+)\s*(.*?)\s*""".r
  private val EmptyLinePattern = """\s*""".r
}
