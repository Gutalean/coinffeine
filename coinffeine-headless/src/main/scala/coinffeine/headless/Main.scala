package coinffeine.headless

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.status.NopStatusListener
import org.slf4j.LoggerFactory

import coinffeine.peer.api.impl.ProductionCoinffeineComponent
import coinffeine.peer.config.user.LocalAppDataDir
import coinffeine.peer.pid.PidFile

object Main extends ProductionCoinffeineComponent {

  def main(args: Array[String]): Unit = {
    configureQuietLoggingInitialization()
    acquirePidFile()
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

  private def acquirePidFile(): Unit = {
    val pidFile = new PidFile(LocalAppDataDir().toFile)
    pidFile.acquire() match {
      case PidFile.Acquired => Success {
        Runtime.getRuntime.addShutdownHook(new Thread() {
          override def run(): Unit = pidFile.release()
        })
      }
      case PidFile.AlreadyRunning(pid) =>
        println(s"Coinffeine is already running with pid $pid")
        System.exit(0)
      case PidFile.CannotCreate(cause) =>
        throw cause
    }
  }
}
