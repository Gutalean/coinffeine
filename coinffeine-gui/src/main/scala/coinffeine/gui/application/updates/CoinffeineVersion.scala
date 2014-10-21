package coinffeine.gui.application.updates

case class CoinffeineVersion(major: Int, minor: Int, revision: Int, build: String) {

  def isNewestThan(other: CoinffeineVersion): Boolean =
    this.major >= other.major &&
    this.minor >= other.minor &&
    this.revision >= other.revision
}

object CoinffeineVersion {
  val Current = CoinffeineVersion(major = 0, minor = 1, revision = 0, build = "SNAPSHOT")
}
