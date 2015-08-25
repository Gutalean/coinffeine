package coinffeine.peer.config

import java.io.File

import com.typesafe.config.Config

class InMemoryConfigProvider(config: Config, override val dataPath: File) extends ConfigProvider {

  override protected def readConfig() = config

  override protected def writeConfig(config: Config) = {}
}
