package coinffeine.peer.config.daemon

import java.io.File

import com.typesafe.config.{ConfigFactory, Config}
import org.slf4j.LoggerFactory

import coinffeine.peer.config.ConfigProvider

private[daemon] class DaemonConfigProvider(configFile: File) extends ConfigProvider {
  import DaemonConfigProvider._

  /** Retrieve the user configuration. */
  override def userConfig: Config =
    if (configFile.isFile) ConfigFactory.parseFile(configFile)
    else ConfigFactory.empty()

  override def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean): Unit = {
    Log.error("Saving user config is not supported for daemon configurations")
    throw new UnsupportedOperationException("Saving user configuration is unsupported")
  }
}

private object DaemonConfigProvider {
  val Log = LoggerFactory.getLogger(classOf[DaemonConfigProvider])
}
