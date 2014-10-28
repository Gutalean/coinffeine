package coinffeine.peer.config.user

import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import scala.collection.JavaConversions._

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import coinffeine.peer.config.ConfigProvider

private[user] class UserFileConfigProvider(filename: String) extends ConfigProvider {

  private val configRenderOpts = ConfigRenderOptions.defaults()
    .setComments(true)
    .setOriginComments(false)
    .setFormatted(true)
    .setJson(false)

  private val _userConfig: AtomicReference[Option[Config]] = new AtomicReference(None)

  override def userConfig = {
    if (_userConfig.get.isEmpty) {
      _userConfig.compareAndSet(None, Some(ConfigFactory.parseFile(userConfigFile().toFile)))
    }
    _userConfig.get.get
  }

  override def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean = true): Unit = {
    val config = if (dropReferenceValues) diff(userConfig, referenceConfig) else userConfig
    val rendered = config.root().render(configRenderOpts)
    val file = new FileOutputStream(userConfigFile().toFile)
    try {
      file.write(rendered.getBytes(Charset.defaultCharset()))
    } finally {
      file.close()
    }
    _userConfig.set(None)
  }

  def userConfigFile(): Path = LocalAppDataDir.getFile(filename)

  private def diff(c1: Config, c2: Config): Config = {
    val c1Items = c1.entrySet().map(entry => entry.getKey -> entry.getValue)
    val c2Items = c2.entrySet().map(entry => entry.getKey -> entry.getValue)
    val c3Items = c1Items.diff(c2Items)
    c3Items.foldLeft(ConfigFactory.empty()) { case (conf, (key, value)) =>
        conf.withValue(key, value)
    }
  }
}

object UserFileConfigProvider {

  val DefaultUserSettingsFilename = "user-settings.conf"

  def apply(filename: String = DefaultUserSettingsFilename): UserFileConfigProvider =
    new UserFileConfigProvider(filename)
}
