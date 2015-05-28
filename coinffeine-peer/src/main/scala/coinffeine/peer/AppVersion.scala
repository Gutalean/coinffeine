package coinffeine.peer

import scalaz.Ordering._
import scalaz.Scalaz._

case class AppVersion(major: Int, minor: Int, revision: Int, tag: Option[String] = None) {

  override def toString = {
    val printableTag = tag.map("-" + _).getOrElse("")
    s"$major.$minor.$revision$printableTag"
  }

  def isNewerThan(other: AppVersion): Boolean = compareTo(other) == GT

  def compareTo(other: AppVersion): scalaz.Ordering =
    (this.major ?|? other.major) |+|
    (this.minor ?|? other.minor) |+|
    (this.revision ?|? other.revision) |+|
    compareTags(this.tag, other.tag)

  /** Compare tags considering absence of tag as a newer version */
  private def compareTags(leftTag: Option[String], rightTag: Option[String]): scalaz.Ordering =
    (leftTag, rightTag) match {
      case (None, None) => EQ
      case (None, Some(_)) => GT
      case (Some(_), None) => LT
      case (Some(left), Some(right)) => left ?|? right
    }
}

object AppVersion {

  private val VersionPattern = """(\d+)\.(\d+)(?:\.(\d+))?(?:-(.*))?""".r

  def apply(major: Int, minor: Int, revision: Int, tag: String): AppVersion =
    AppVersion(major, minor, revision, Some(tag))

  def apply(version: String): AppVersion = version match {
    case VersionPattern(major, minor, optionalRevision, optionalBuild) => AppVersion(
      major = major.toInt,
      minor = minor.toInt,
      revision = Option(optionalRevision).map(_.toInt).getOrElse(0),
      tag = Option(optionalBuild)
    )
    case _ =>
      throw new IllegalArgumentException(s"Cannot parse '$version' as a version number")
  }

  val Current = AppVersion(BuildInfo.version)
}
