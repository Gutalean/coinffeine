package coinffeine.headless

import coinffeine.headless.commands.{ListOrdersCommand, StatusCommand}
import coinffeine.headless.prompt.ConnectionStatusPrompt
import coinffeine.headless.shell.{Command, Shell}
import coinffeine.peer.api.CoinffeineApp

class CoinffeineInterpreter(app: CoinffeineApp)
  extends Shell(new ConnectionStatusPrompt(app), CoinffeineInterpreter.commands(app))

object CoinffeineInterpreter {
  private def commands(app: CoinffeineApp): Seq[Command] = Seq(
    new StatusCommand(app),
    new ListOrdersCommand(app.network.orders)
  )
}
