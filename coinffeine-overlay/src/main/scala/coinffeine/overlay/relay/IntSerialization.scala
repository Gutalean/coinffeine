package coinffeine.overlay.relay

import akka.util.{ByteString, ByteStringBuilder}

private object IntSerialization {
  private val ByteMasks = Seq(0xFF000000, 0xFF0000, 0xFF00, 0xFF)
  private val Offsets = Seq(24, 16, 8, 0)
  private val IntBytes = 4

  def serialize(value: Int, output: ByteStringBuilder): Unit = {
    Offsets.foreach { offset =>
      output.putByte((value >> offset) & 0xFF toByte)
    }
  }

  def deserialize(bytes: ByteString): Int = {
    require(bytes.size >= IntBytes)
    var value = 0
    for ((offset, mask, byte) <- (Offsets, ByteMasks, bytes).zipped) {
      value |= byte << offset & mask
    }
    value
  }
}
