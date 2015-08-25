package coinffeine.peer.config.user

import java.io.{File, FileOutputStream}
import java.nio.charset.Charset

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import coinffeine.peer.config.ConfigStore

class UserFileConfigStore(
    override val dataPath: File,
    filename: String = UserFileConfigStore.DefaultUserSettingsFilename) extends ConfigStore {

  private val configRenderOpts = ConfigRenderOptions.defaults()
    .setComments(true)
    .setOriginComments(false)
    .setFormatted(true)
    .setJson(false)

  private val userConfigFile = new File(dataPath, filename)

  override def readConfig() = ConfigFactory.parseFile(userConfigFile)

  override def writeConfig(config: Config) = {
    val rendered = config.root().render(configRenderOpts)
    ensureUserConfigExists()
    val file = new FileOutputStream(userConfigFile)
    try {
      file.write(rendered.getBytes(Charset.defaultCharset()))
    } finally {
      file.close()
    }
  }

  private def ensureUserConfigExists(): Unit = {
    if (!userConfigFile.exists()) {
      require(userConfigFile.createNewFile(), s"cannot create config file $userConfigFile")
    } else {
      require(userConfigFile.isFile, s"$userConfigFile exists but it is not a file as expected")
    }
  }
}

private object UserFileConfigStore {
  val DefaultUserSettingsFilename = "user-settings.conf"
}
