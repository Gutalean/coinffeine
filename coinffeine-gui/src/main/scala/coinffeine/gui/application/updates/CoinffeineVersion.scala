package coinffeine.gui.application.updates

case class CoinffeineVersion(major: Int, minor: Int, revision: Int, build: String = "") {

  private lazy val printableBuild = if (build.isEmpty) build else s"-$build"

  override def toString = s"v$major.$minor.$revision$printableBuild"

  def isNewerThan(other: CoinffeineVersion): Boolean =
    this.major > other.major ||
    (this.major == other.major && this.minor > other.minor) ||
    (this.major == other.major && this.minor == other.minor && this.revision > other.revision)
}

object CoinffeineVersion {
  val Current = CoinffeineVersion(major = 0, minor = 1, revision = 0, build = "SNAPSHOT")
}
