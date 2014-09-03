package coinffeine.peer.config

import java.io.File
import java.nio.file.{Paths, Path}

import com.typesafe.config.ConfigFactory

class FileSettingsProvider(filename: String) extends SettingsProvider {

  override lazy val userConfig = ConfigFactory.parseFile(userConfigFile().toFile)

  def userConfigFile(): Path = {
    val path = userSettingsPath().resolve(filename)
    ensureUserSettingsFileExists(path.toFile)
    path
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

object FileSettingsProvider {

  val DefaultUserSettingsFilename = "user-settings.conf"

  def apply(filename: String = DefaultUserSettingsFilename): FileSettingsProvider =
    new FileSettingsProvider(filename)
}
