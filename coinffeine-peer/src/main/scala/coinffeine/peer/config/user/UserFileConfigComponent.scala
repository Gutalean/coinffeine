package coinffeine.peer.config.user

import java.io.File

import coinffeine.common.Platform
import coinffeine.peer.config.{ConfigComponent, ConfigProvider}

trait UserFileConfigComponent extends ConfigComponent {

  def commandLineArgs: List[String]

  lazy val configProvider: ConfigProvider = new UserFileConfigProvider(dataPath)

  def dataPath: File = {
    val defaultPath = Platform.detect().userSettingsPath().toAbsolutePath.toFile
    val path = commandLineArgs match {
      case Seq() => defaultPath
      case Seq("--data-path", dataPath) => new File(dataPath)
      case _ =>
        println(
          s"""Can't understand arguments: '$commandLineArgs'
             |
             |Usage: <command> [--data-path <path>]
             |
             |    data-path: directory for configurations and data files ($defaultPath by default)
           """.stripMargin)
        System.exit(-1)
        throw new IllegalStateException("Should not reach this line")
    }
    ensureDirExists(path)
    path
  }

  private def ensureDirExists(dir: File): Unit = {
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IllegalStateException(s"cannot create local app data directory in $dir")
      }
    } else if (!dir.isDirectory) {
      throw new IllegalStateException(
        s"there is a file in $dir where local app data directory was expected")
    }
  }
}
