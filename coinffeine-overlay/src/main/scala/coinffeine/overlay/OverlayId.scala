package coinffeine.overlay

case class OverlayId(value: BigInt) {
  require(value >= OverlayId.MinValue && value <= OverlayId.MaxValue, s"$value is out of range")
  override def toString = s"OverlayId(${value.toString(16)})"
}

object OverlayId {
  val MinValue = BigInt(0)
  val MaxValue = MinValue.setBit(160) - 1
}
