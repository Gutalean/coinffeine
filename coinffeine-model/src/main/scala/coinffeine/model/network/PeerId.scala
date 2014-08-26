package coinffeine.model.network

/** An identifier used by nodes of Coinffeine network. */
sealed trait NodeId

/** A special case of node ID that identifies a (regular) peer on the Coinffeine network. */
case class PeerId(value: String) extends NodeId {
  override def toString = s"peer $value"
}

/** A special case of node ID that identifies the broker ID on the Coinffeine network. */
case object BrokerId extends NodeId
