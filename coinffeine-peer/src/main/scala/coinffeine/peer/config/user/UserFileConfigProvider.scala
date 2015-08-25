package coinffeine.peer.config.user

import java.io.{File, FileOutputStream}
import java.nio.charset.Charset

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import coinffeine.peer.config.ConfigProvider

class UserFileConfigProvider(
    override val dataPath: File,
    filename: String = UserFileConfigProvider.DefaultUserSettingsFilename) extends ConfigProvider {

  private val configRenderOpts = ConfigRenderOptions.defaults()
    .setComments(true)
    .setOriginComments(false)
    .setFormatted(true)
    .setJson(false)

  private val userConfigFile = new File(dataPath, filename)

  override protected def readConfig() = ConfigFactory.parseFile(userConfigFile)

  override protected def writeConfig(config: Config) = {
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

private object UserFileConfigProvider {
  val DefaultUserSettingsFilename = "user-settings.conf"
}
