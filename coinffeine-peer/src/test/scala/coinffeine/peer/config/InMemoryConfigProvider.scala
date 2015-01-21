package coinffeine.peer.config

import java.io.File

import com.typesafe.config.Config

class InMemoryConfigProvider(override val userConfig: Config,
                             override val dataPath: File) extends ConfigProvider {

  override def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean = true) = {}
}
