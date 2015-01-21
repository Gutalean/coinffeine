package coinffeine.peer.config.user

import java.io.File

import coinffeine.peer.config.{ConfigComponent, ConfigProvider}

trait UserFileConfigComponent extends ConfigComponent {

  def commandLineArgs: List[String]

  lazy val configProvider: ConfigProvider = new UserFileConfigProvider(dataPath)

  def dataPath: File = {
    val defaultPath = LocalAppDataDir().toAbsolutePath.toFile
    commandLineArgs match {
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
  }
}
