package coinffeine.peer.config

import java.io.File

import com.typesafe.config.Config

class InMemoryConfigStore(var config: Config, override val dataPath: File) extends ConfigStore {

  override def readConfig() = config

  override def writeConfig(newConfig: Config) = { config = newConfig }
}
