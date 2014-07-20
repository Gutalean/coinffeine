package coinffeine.protocol.serialization

import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

trait ProtocolSerialization {
  def fromProtobuf(protoMessage: CoinffeineMessage): PublicMessage
  def toProtobuf(message: PublicMessage): CoinffeineMessage
}
