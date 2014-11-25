package coinffeine.headless

import scala.concurrent.duration._

import coinffeine.peer.api.impl.ProductionCoinffeineComponent

object Main extends ProductionCoinffeineComponent {

  def main(args: Array[String]): Unit = {
    if (configProvider.okPaySettings().userAccount.isEmpty) {
      println("You should run the wizard or configure manually the application")
      System.exit(-1)
    }
    val startupTimeout = 20.seconds
    app.startAndWait(startupTimeout)
    try {
      new CoinffeineInterpreter(app).run()
    } finally {
      app.stopAndWait(startupTimeout)
    }
    System.exit(0)
  }
}
