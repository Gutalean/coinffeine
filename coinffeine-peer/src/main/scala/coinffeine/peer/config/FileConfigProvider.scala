package coinffeine.peer.config

import java.io.{File, FileOutputStream}
import java.nio.charset.Charset
import java.nio.file.{Path, Paths}
import java.util.concurrent.atomic.AtomicReference
import scala.collection.JavaConversions._

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

class FileConfigProvider(filename: String) extends ConfigProvider {

  private var _userConfig: AtomicReference[Option[Config]] = new AtomicReference(None)

  override def userConfig = {
    if (_userConfig.get.isEmpty) {
      _userConfig.compareAndSet(None, Some(ConfigFactory.parseFile(userConfigFile().toFile)))
    }
    _userConfig.get.get
  }

  override def saveUserConfig(userConfig: Config, dropReferenceValues: Boolean = true): Unit = {
    val config = if (dropReferenceValues) diff(userConfig, referenceConfig) else userConfig
    val rendered = config.root().render(ConfigRenderOptions.concise())
    val file = new FileOutputStream(userConfigFile().toFile)
    try {
      file.write(rendered.getBytes(Charset.defaultCharset()))
    } finally {
      file.close()
    }
    _userConfig.set(None)
  }

  def userConfigFile(): Path = {
    val path = userSettingsPath().resolve(filename)
    ensureUserSettingsFileExists(path.toFile)
    path
  }

  private def diff(c1: Config, c2: Config): Config = {
    val c1Items = c1.root().unwrapped().toSet
    val c2Items = c2.root().unwrapped().toSet
    ConfigFactory.parseMap(c1Items.diff(c2Items).toMap[String, AnyRef])
  }

  private def userSettingsPath(): Path = {
    val path = osCodename match {
      case "WIN" => windowsUserSettingsPath
      case "MAC" => macUserSettingsPath
      case "LIN" => linuxUserSettingsPath
      case _ => throw new IllegalStateException(
        s"cannot determine your operating system family from $osCodename")
    }
    ensureUserSettingsDirExists(path.toFile)
    path
  }

  private def osCodename = System.getProperty("os.name").take(3).toUpperCase

  private def windowsUserSettingsPath: Path = Paths.get(System.getenv("APPDATA"), "Coinffeine")

  private def macUserSettingsPath: Path =
    Paths.get(System.getProperty("user.home"), "Library", "Application Support", "Coinffeine")

  private def linuxUserSettingsPath: Path =
    Paths.get(System.getProperty("user.home"), ".coinffeine")

  private def ensureUserSettingsDirExists(dir: File): Unit = {
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IllegalStateException(s"cannot create user settings directory in $dir")
      }
    } else if (!dir.isDirectory) {
      throw new IllegalStateException(
        s"there is a file in $dir where settings directory was expected")
    }
  }

  private def ensureUserSettingsFileExists(file: File): Unit = {
    if (!file.exists()) {
      if (!file.createNewFile()) {
        throw new IllegalStateException(s"cannot create user settings file in $file")
      }
    } else if (!file.isFile) {
      throw new IllegalStateException(
        s"there is a directory in $file where settings file was expected")
    }
  }
}

object FileConfigProvider {

  val DefaultUserSettingsFilename = "user-settings.conf"

  def apply(filename: String = DefaultUserSettingsFilename): FileConfigProvider =
    new FileConfigProvider(filename)
}
