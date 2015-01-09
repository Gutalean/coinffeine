package coinffeine.overlay.relay.messages

import akka.util.ByteString

private[relay] object ProtobufFrame {
  def serialize(message: Message): ByteString =
    Frame(ProtobufConversion.toByteString(message)).serialize
}
