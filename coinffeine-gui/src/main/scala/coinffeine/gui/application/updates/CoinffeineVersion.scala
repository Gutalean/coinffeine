package coinffeine.gui.application.updates

import coinffeine.gui.application.BuildInfo

case class CoinffeineVersion(major: Int, minor: Int, revision: Int, tag: Option[String] = None) {

  override def toString = {
    val printableTag = tag.map("-" + _).getOrElse("")
    s"v$major.$minor.$revision$printableTag"
  }

  def isNewerThan(other: CoinffeineVersion): Boolean =
    this.major > other.major ||
    (this.major == other.major && this.minor > other.minor) ||
    (this.major == other.major && this.minor == other.minor && this.revision > other.revision) ||
    hasNewerTagThan(other)

  private def hasNewerTagThan(other: CoinffeineVersion): Boolean = (this.tag, other.tag) match {
    case (Some(thisTag), Some(otherTag)) => thisTag.compareTo(otherTag) > 0
    case (None, Some(_)) => true
    case _ => false
  }
}

object CoinffeineVersion {

  private val VersionPattern = """(\d+)\.(\d+)(?:\.(\d+))?(?:-(.*))?""".r

  def apply(major: Int, minor: Int, revision: Int, tag: String): CoinffeineVersion =
    CoinffeineVersion(major, minor, revision, Some(tag))

  def apply(version: String): CoinffeineVersion = version match {
    case VersionPattern(major, minor, optionalRevision, optionalBuild) => CoinffeineVersion(
      major = major.toInt,
      minor = minor.toInt,
      revision = Option(optionalRevision).map(_.toInt).getOrElse(0),
      tag = Option(optionalBuild)
    )
    case _ =>
      throw new IllegalArgumentException(s"Cannot parse '$version' as a version number")
  }

  val Current = CoinffeineVersion(BuildInfo.version)
}
