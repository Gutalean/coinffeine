package coinffeine.peer.config.daemon

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import coinffeine.peer.config.ConfigStore

private[daemon] class DaemonConfigStore(configFile: File, override val dataPath: File)
  extends ConfigStore with LazyLogging {

  override def readConfig() =
    if (configFile.isFile) ConfigFactory.parseFile(configFile)
    else ConfigFactory.empty()

  override def writeConfig(config: Config): Unit = {
    logger.error("Saving user config is not supported for daemon configurations")
    throw new UnsupportedOperationException("Saving user configuration is unsupported")
  }
}
