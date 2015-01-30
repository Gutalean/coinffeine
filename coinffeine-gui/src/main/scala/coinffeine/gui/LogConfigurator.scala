package coinffeine.gui

import java.io.{File, IOException}
import java.net.URL
import scala.util.Try

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import coinffeine.common.Platform
import coinffeine.peer.config.ConfigProvider

/** Configures logging using an external file in the user configuration directory (and automatically
  * creates it with a default configuration if missing). If there are problems, it tries to go with
  * the default configuration.
  */
object LogConfigurator {

  private val Context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
  private val ConfigFilename = "logging.xml"
  private val DefaultConfig = Option(getClass.getResource("/logback.xml")).getOrElse(
    throw new NoSuchElementException("Missing default logging configuration"))
  private val DataDirProperty = "COINFFEINE_DATA_DIR"

  def configure(configProvider: ConfigProvider): Unit = {
    val configFile = new File(configProvider.dataPath, ConfigFilename)

    ensureExistenceOfExternalConfiguration()
    fallbackToInternalConfig(tryToConfigureLogging(configFile.toURI.toURL)).get

    def ensureExistenceOfExternalConfiguration(): Unit = {
      if (!configFile.exists()) {
        createExternalConfiguration()
      }
    }

    /** Create the external logging configuration by copying the default one bundled with the app */
    def createExternalConfiguration(): Unit = {
      try {
        configFile.getParentFile.mkdirs()
        FileUtils.copyURLToFile(DefaultConfig, configFile)
      } catch {
        case ex: IOException =>
          println(s"LOGGING WARNING: cannot create $configFile")
          ex.printStackTrace()
      }
    }

    def fallbackToInternalConfig(configurationAttempt: Try[Unit]): Try[Unit] =
      configurationAttempt.recoverWith {
        case ex: JoranException => tryToConfigureLogging(DefaultConfig)
      }

    def tryToConfigureLogging(configuration: URL): Try[Unit] = Try {
      // Log config files expect this property to be defined so the log
      // file may know what's the data dir
      System.setProperty(DataDirProperty, configProvider.dataPath.toString)

      val configurator = new JoranConfigurator()
      configurator.setContext(Context)
      Context.reset()
      configurator.doConfigure(configuration)
    }.recover {
      case ex: JoranException =>
        // This pretty-prints the errors in the JoranException
        StatusPrinter.printInCaseOfErrorsOrWarnings(Context)
        throw ex
    }
  }
}
