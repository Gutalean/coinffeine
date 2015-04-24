package coinffeine.protocol

case class Version(major: Int, minor: Int) {
  override def toString = s"$major.$minor"
}

object Version {
  val Current = Version(major = 0, minor = 3)
}
