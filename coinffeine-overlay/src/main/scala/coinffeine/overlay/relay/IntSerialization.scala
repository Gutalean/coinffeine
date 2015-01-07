package coinffeine.overlay.relay

import akka.util.{ByteString, ByteStringBuilder}

private object IntSerialization {
  val Bytes = 4
  private val ByteMasks = Seq(0xFF000000, 0xFF0000, 0xFF00, 0xFF)
  private val Offsets = Seq(24, 16, 8, 0)

  def serialize(value: Int, output: ByteStringBuilder): Unit = {
    Offsets.foreach { offset =>
      output.putByte((value >> offset) & 0xFF toByte)
    }
  }

  def deserialize(bytes: ByteString): Int = {
    require(bytes.size >= Bytes)
    var value = 0
    for ((offset, mask, byte) <- (Offsets, ByteMasks, bytes).zipped) {
      value |= byte << offset & mask
    }
    value
  }
}
