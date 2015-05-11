package coinffeine.headless

import java.io.File

import coinffeine.headless.commands._
import coinffeine.headless.prompt.ConnectionStatusPrompt
import coinffeine.headless.shell.{Command, Shell}
import coinffeine.model.order.{Ask, Bid}
import coinffeine.peer.api.CoinffeineApp

class CoinffeineInterpreter(app: CoinffeineApp, historyFile: File)
  extends Shell(new ConnectionStatusPrompt(app), CoinffeineInterpreter.commands(app)) {
  usePersistentHistory(historyFile)

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = {
      flush()
    }
  }))
}

object CoinffeineInterpreter {
  private def commands(app: CoinffeineApp): Seq[Command] = {
    val actions = Seq(
      new StatusCommand(app),
      new ListOrdersCommand(app.network.orders),
      new ShowOrderDetailsCommand(app.network),
      new OpenOrderCommand(Bid, app.network),
      new OpenOrderCommand(Ask, app.network),
      new CancelOrderCommand(app.network)
    )
    actions :+ new HelpCommand(actions)
  }
}
