package coinffeine.gui.application.updates

import scala.annotation.tailrec
import scalaz.Ordering.{EQ, GT, LT}
import scalaz.std.anyVal._
import scalaz.std.string._
import scalaz.syntax.order._

import coinffeine.gui.application.BuildInfo

case class CoinffeineVersion(major: Int, minor: Int, revision: Int, tag: Option[String] = None) {

  override def toString = {
    val printableTag = tag.map("-" + _).getOrElse("")
    s"v$major.$minor.$revision$printableTag"
  }

  def isNewerThan(other: CoinffeineVersion): Boolean = isGreater(Seq(
    this.major ?|? other.major,
    this.minor ?|? other.minor,
    this.revision ?|? other.revision,
    compareTags(this.tag, other.tag)
  ))

  /** Combine orderings having the first ones more priority */
  @tailrec
  private def isGreater(orderings: Seq[scalaz.Ordering]): Boolean = orderings match {
    case Seq(GT, _*) => true
    case Seq(EQ) => false
    case Seq(EQ, remaining @ _*) => isGreater(remaining)
    case Seq(LT, _*) => false
  }

  /** Compare tags considering absence of tag as a newer version */
  private def compareTags(leftTag: Option[String], rightTag: Option[String]): scalaz.Ordering =
    (leftTag, rightTag) match {
      case (None, None) => EQ
      case (None, Some(_)) => GT
      case (Some(_), None) => LT
      case (Some(left), Some(right)) => left ?|? right
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
