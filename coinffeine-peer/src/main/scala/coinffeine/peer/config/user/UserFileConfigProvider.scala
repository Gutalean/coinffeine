package coinffeine.peer.config.user

import java.io.{File, FileOutputStream}
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import scala.collection.JavaConversions._

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import coinffeine.peer.config.ConfigProvider

private class UserFileConfigProvider(
    override val dataPath: File,
    filename: String = UserFileConfigProvider.DefaultUserSettingsFilename) extends ConfigProvider {

  private val configRenderOpts = ConfigRenderOptions.defaults()
    .setComments(true)
    .setOriginComments(false)
    .setFormatted(true)
    .setJson(false)

  private val userConfigFile = new File(dataPath, filename)
  private val _userConfig: AtomicReference[Option[Config]] = new AtomicReference(None)

  override def userConfig = {
    if (_userConfig.get.isEmpty) {
      _userConfig.compareAndSet(None, Some(ConfigFactory.parseFile(userConfigFile)))
    }
    _userConfig.get.get
  }

  override def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean = true): Unit = {
    val config = if (dropReferenceValues) diff(userConfig, referenceConfig) else userConfig
    val rendered = config.root().render(configRenderOpts)
    ensureUserConfigExists()
    val file = new FileOutputStream(userConfigFile)
    try {
      file.write(rendered.getBytes(Charset.defaultCharset()))
    } finally {
      file.close()
    }
    _userConfig.set(None)
  }

  private def ensureUserConfigExists(): Unit = {
    if (!userConfigFile.exists()) {
      require(userConfigFile.createNewFile(), s"cannot create config file $userConfigFile")
    } else {
      require(userConfigFile.isFile, s"$userConfigFile exists but it is not a file as expected")
    }
  }

  private def diff(c1: Config, c2: Config): Config = {
    val c1Items = c1.entrySet().map(entry => entry.getKey -> entry.getValue)
    val c2Items = c2.entrySet().map(entry => entry.getKey -> entry.getValue)
    val c3Items = c1Items.diff(c2Items)
    c3Items.foldLeft(ConfigFactory.empty()) { case (conf, (key, value)) =>
        conf.withValue(key, value)
    }
  }
}

private object UserFileConfigProvider {
  val DefaultUserSettingsFilename = "user-settings.conf"
}
