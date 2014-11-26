package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.prompt.ANSIText.Bold
import coinffeine.headless.shell.Command

class HelpCommand(commands: Seq[Command]) extends Command {
  override val keyword = "help"
  override val description = "prints help about commands"
  override val usage =
    """
      |help
      |
      |  prints the list of all available commands
      |
      |help <command>
      |
      |  prints help about a particular command
    """.stripMargin

  private val allCommands = (commands :+ this).sortBy(_.keyword)

  override def apply(output: PrintWriter, args: String): Unit = {
    args.trim match {
      case "" => generalHelp(output)
      case name => printCommandHelp(output, allCommands.find(_.keyword == name).get)
    }
  }

  private def generalHelp(output: PrintWriter): Unit = {
    listAllCommands(output)
    output.println()
    output.println("You can get more specific information running " + Bold("help <command>"))
  }

  private def listAllCommands(output: PrintWriter): Unit = {
    allCommands.foreach { command =>
      printCommandDescription(output, command)
    }
  }

  private def printCommandHelp(output: PrintWriter, command: Command): Unit = {
    printCommandDescription(output, command)
    printCommandUsage(output, command)
  }

  private def printCommandDescription(output: PrintWriter, command: Command): Unit = {
    output.format("%s %s%n", Bold(command.keyword + ":"), command.description)
  }

  private def printCommandUsage(output: PrintWriter, command: Command): Unit = {
    output.println(Bold("Usage:"))
    output.println(command.usage)
  }
}
