package coinffeine.headless

import coinffeine.headless.prompt.ConnectionStatusPrompt
import coinffeine.headless.shell.{Command, Shell}
import coinffeine.peer.api.CoinffeineApp

class CoinffeineInterpreter(app: CoinffeineApp)
  extends Shell(new ConnectionStatusPrompt(app), CoinffeineInterpreter.Commands)

object CoinffeineInterpreter {
  private val Commands = Seq.empty[Command]
}
