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

/** Configures logging using an external file in the user configuration directory (and automatically
  * creates it with a default configuration if missing). If there are problems, it tries to go with
  * the default configuration.
  */
object LogConfigurator {

  private val Context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
  private val ConfigFilename = "logging.xml"
  private val ConfigFile = new File(Platform.detect().userSettingsPath().toFile, ConfigFilename)
  private val DefaultConfig = Option(getClass.getResource("/logback.xml")).getOrElse(
    throw new NoSuchElementException("Missing default logging configuration"))

  def configure(): Unit = {
    ensureExistenceOfExternalConfiguration()
    fallbackToInternalConfig(tryToConfigureLogging(ConfigFile.toURI.toURL)).get
  }

  private def ensureExistenceOfExternalConfiguration(): Unit = {
    if (!ConfigFile.exists()) {
      createExternalConfiguration()
    }
  }

  /** Create the external logging configuration by copying the default one bundled with the app */
  private def createExternalConfiguration(): Unit = {
    try {
      ConfigFile.getParentFile.mkdirs()
      FileUtils.copyURLToFile(DefaultConfig, ConfigFile)
    } catch {
      case ex: IOException =>
        println(s"LOGGING WARNING: cannot create $ConfigFile")
        ex.printStackTrace()
    }
  }

  private def fallbackToInternalConfig(configurationAttempt: Try[Unit]): Try[Unit] =
    configurationAttempt.recoverWith {
      case ex: JoranException => tryToConfigureLogging(DefaultConfig)
    }

  private def tryToConfigureLogging(configuration: URL): Try[Unit] = Try {
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
