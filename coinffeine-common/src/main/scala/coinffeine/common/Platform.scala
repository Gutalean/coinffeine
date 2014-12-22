package coinffeine.common

import java.nio.file.{Paths, Path}

/** Taxonomy of supported platforms */
sealed trait Platform {
  def userSettingsPath(): Path
}

object Platform {
  case object Linux extends Platform {
    override def userSettingsPath() = Paths.get(System.getProperty("user.home"), ".coinffeine")
  }

  case object Mac extends Platform {
    override def userSettingsPath() =
      Paths.get(System.getProperty("user.home"), "Library", "Application Support", "Coinffeine")
  }

  case object Windows extends Platform {
    override def userSettingsPath() = Paths.get(System.getenv("APPDATA"), "Coinffeine")
  }

  /** Platform detection based on system properties */
  @throws[IllegalStateException]("when the platform cannot be detected because it is unsupported")
  def detect(): Platform = osCodename match {
    case "LIN" => Linux
    case "MAC" => Mac
    case "WIN" => Windows
    case _ => throw new IllegalStateException(
      s"cannot determine your operating system family from $osCodename")
  }

  private def osCodename = System.getProperty("os.name").take(3).toUpperCase
}
