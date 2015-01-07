package coinffeine.overlay.relay

import java.nio.ByteOrder

import akka.util.{ByteString, ByteStringBuilder}

private object IntSerialization {
  val Bytes = 4

  def serialize(value: Int, output: ByteStringBuilder): Unit = {
    output.putInt(value)(ByteOrder.BIG_ENDIAN)
  }

  def deserialize(bytes: ByteString): Int = bytes.asByteBuffer.getInt
}
