package coinffeine.peer.config

import java.io.File

import com.typesafe.config.Config

trait ConfigStore {

  /** Get the path where config is obtained */
  def dataPath: File

  def readConfig(): Config

  def writeConfig(config: Config): Unit
}
