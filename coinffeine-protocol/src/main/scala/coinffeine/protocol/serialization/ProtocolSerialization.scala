package coinffeine.protocol.serialization

import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

trait ProtocolSerialization {
  def fromProtobuf(protoMessage: CoinffeineMessage): (PublicMessage, PeerId)
  def toProtobuf(message: PublicMessage, id: PeerId): CoinffeineMessage
}
