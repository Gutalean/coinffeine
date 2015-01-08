package coinffeine.overlay.relay

import akka.util.ByteString

private object ProtobufFrame {
  def serialize(message: Message): ByteString =
    Frame(ProtobufConversion.toByteString(message)).serialize
}
