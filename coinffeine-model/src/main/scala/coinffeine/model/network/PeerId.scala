package coinffeine.model.network

import java.security.SecureRandom

/** An identifier used by nodes of Coinffeine network. */
sealed trait NodeId

/** A special case of node ID that identifies a (regular) peer on the Coinffeine network. */
case class PeerId(value: String) extends NodeId {
  require(PeerId.Pattern.unapplySeq(value).isDefined,
    s"A hex value up to 40 digits was expected ($value given)")

  override def toString = s"peer:$canonical"

  override def equals(obj: scala.Any): Boolean = obj match {
    case other: PeerId => canonical == other.canonical
    case _ => false
  }

  override def hashCode(): Int = canonical.hashCode

  private def canonical: String = value.dropWhile(_ == '0').toLowerCase
}

object PeerId {
  private val rnd = new SecureRandom()
  private val Pattern = "[0-9a-fA-F]{1,40}".r

  /** Produce a peer id by hashing a human-friendly name */
  def hashOf(name: String): PeerId = PeerId(name.hashCode.toHexString)

  def random(): PeerId = PeerId(Seq.fill(40)(rnd.nextInt(16)).map(_.toHexString).mkString)
}

/** A special case of node ID that identifies the broker ID on the Coinffeine network. */
case object BrokerId extends NodeId
