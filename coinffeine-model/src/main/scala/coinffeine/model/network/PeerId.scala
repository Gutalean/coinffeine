package coinffeine.model.network

/** An identifier used by nodes of Coinffeine network. */
sealed trait NodeId

/** A special case of node ID that identifies a (regular) peer on the Coinffeine network. */
case class PeerId(value: String) extends NodeId {
  require(PeerId.Pattern.unapplySeq(value).isDefined,
    s"A hex value up to 40 lowercase digits was expected ($value given)")
  override def toString = s"peer:$value"
}

object PeerId {
  private val Pattern = "[0-9a-f]{1,40}".r

  /** Produce a peer id by hashing a human-friendly name */
  def hashOf(name: String): PeerId = PeerId(name.hashCode.toHexString)
}

/** A special case of node ID that identifies the broker ID on the Coinffeine network. */
case object BrokerId extends NodeId
