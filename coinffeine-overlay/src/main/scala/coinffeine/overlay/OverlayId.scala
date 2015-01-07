package coinffeine.overlay

case class OverlayId(value: BigInt) {
  require(value >= OverlayId.MinValue && value <= OverlayId.MaxValue, s"$value is out of range")
}

object OverlayId {
  val MinValue = BigInt(0)
  val MaxValue = MinValue.setBit(160) - 1
}
