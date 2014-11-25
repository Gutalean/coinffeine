package coinffeine.headless

import scala.concurrent.duration._

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.status.NopStatusListener
import org.slf4j.LoggerFactory

import coinffeine.peer.api.impl.ProductionCoinffeineComponent

object Main extends ProductionCoinffeineComponent {

  def main(args: Array[String]): Unit = {
    configureQuietLoggingInitialization()
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

  private def configureQuietLoggingInitialization(): Unit = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    context.getStatusManager.add(new NopStatusListener)
  }
}
