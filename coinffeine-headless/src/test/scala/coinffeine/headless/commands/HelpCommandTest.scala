package coinffeine.headless.commands

import coinffeine.headless.prompt.ANSIText.Bold

class HelpCommandTest extends CommandTest {

  val commands = Seq(new SpyCommand("command-a"), new SpyCommand("command-b"))
  val help = new HelpCommand(commands.reverse)

  "The help command" should "list alphabetically all commands when invoked without args" in {
    executeCommand(help).trim should include
      s"""
         |${Bold("command-a:")} description of command-a
         |${Bold("command-b:")} description of command-b
         |${Bold("help:")} ${help.description}
       """.stripMargin.trim
  }

  it should "show description and usage of a command when invoked with a command keyword" in {
    executeCommand(help, "command-a") should (
      include("description of command-a") and include(Bold("Usage:") + "\ncommand-a"))
    executeCommand(help, "command-b") should include("command-b")
  }
}
