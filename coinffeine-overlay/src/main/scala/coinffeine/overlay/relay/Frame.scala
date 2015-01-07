package coinffeine.overlay.relay

import akka.util.{ByteString, ByteStringBuilder}

/** Wraps a variable-size binary payload to help with frame delimitation. */
private case class Frame(payload: ByteString) {

  /** Serialize the variable-length payload using:
    *
    *  * A magic number (one byte)
    *  * Payload size (as an big-endian 4-byte integer)
    *  * The payload itself
    */
  def serialize: ByteString = {
    val output = new ByteStringBuilder
    output.putByte(Frame.MagicByte)
    IntSerialization.serialize(payload.size, output)
    output.append(payload)
    output.result()
  }
}

private object Frame {
  val MagicByte: Byte = 1

  sealed trait ParseResult

  /** The input data is correct so far but we need more bytes to complete a frame. */
  case object IncompleteInput extends ParseResult

  /** The input data (complete or incomplete) is invalid. */
  case class FailedParsing(message: String) extends ParseResult

  /** A valid frame was at the beginning of the byte array. */
  case class Parsed(frame: Frame, remainingBytes: ByteString) extends ParseResult

  /** Try to deserialize a frame in the format defined in [[Frame.serialize]] */
  def deserialize(bytes: ByteString): ParseResult = {
    if (bytes.isEmpty) IncompleteInput
    else if (bytes.head != MagicByte)
      FailedParsing(s"Bad magic number: ${bytes.head} instead of $MagicByte")
    else deserializeLengthAndPayload(bytes.drop(1))
  }

  private def deserializeLengthAndPayload(bytes: ByteString): ParseResult =
    if (bytes.size < IntSerialization.Bytes) IncompleteInput
    else {
      val (lengthBytes, remainingBytes) = bytes.splitAt(IntSerialization.Bytes)
      val length = IntSerialization.deserialize(lengthBytes)
      if (length < 0) FailedParsing(s"Invalid length of $length")
      else deserializePayload(length, remainingBytes)
    }

  private def deserializePayload(length: Int, bytes: ByteString): ParseResult =
    if (bytes.size < length) IncompleteInput
    else Parsed(Frame(bytes.take(length)), remainingBytes = bytes.drop(length))
}
