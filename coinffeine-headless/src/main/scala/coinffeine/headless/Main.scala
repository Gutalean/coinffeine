package coinffeine.headless

import java.io.File
import scala.concurrent.duration._
import scala.util.Success
import scala.util.control.NonFatal

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.status.NopStatusListener
import org.slf4j.LoggerFactory

import coinffeine.peer.api.impl.ProductionCoinffeineComponent
import coinffeine.peer.pid.PidFile

object Main {

  private val timeout = 20.seconds

  def main(args: Array[String]): Unit = {
    val coinffeine = new ProductionCoinffeineComponent {
      override def commandLineArgs = args.toList
    }
    configureQuietLoggingInitialization()
    acquirePidFile(coinffeine.configProvider.dataPath)
    if (!coinffeine.configProvider.generalSettings().licenseAccepted) {
      println("You should run the wizard or configure manually the application")
      System.exit(-1)
    }
    try {
      coinffeine.app.startAndWait(timeout)
      val historyFile = new File(coinffeine.configProvider.dataPath, "history")
      new CoinffeineInterpreter(coinffeine.app, historyFile).run()
      System.exit(-1)
    } catch {
      case NonFatal(ex) =>
        ex.printStackTrace()
    } finally {
      coinffeine.app.stopAndWait(timeout)
    }
    System.exit(-1)
  }

  private def configureQuietLoggingInitialization(): Unit = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    context.getStatusManager.add(new NopStatusListener)
  }

  private def acquirePidFile(dataPath: File): Unit = {
    val pidFile = new PidFile(dataPath)
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
