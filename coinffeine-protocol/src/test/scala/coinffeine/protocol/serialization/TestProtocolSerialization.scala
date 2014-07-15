package coinffeine.protocol.serialization

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

class TestProtocolSerialization extends ProtocolSerialization {
  private val underlying = new DefaultProtocolSerialization(
    new TransactionSerialization(CoinffeineUnitTestNetwork))
  var unserializableMessages = Set.empty[PublicMessage]
  var undeserializableMessages = Set.empty[CoinffeineMessage]

  override def toProtobuf(message: PublicMessage, id: PeerId) =
    if (unserializableMessages.contains(message))
      throw new IllegalArgumentException("Cannot serialize")
    else underlying.toProtobuf(message, id)

  override def fromProtobuf(protoMessage: CoinffeineMessage): (PublicMessage, PeerId) =
    if (undeserializableMessages.contains(protoMessage))
      throw new IllegalArgumentException("Cannot deserialize")
    else underlying.fromProtobuf(protoMessage)

  def wontSerialize(message: PublicMessage): Unit = unserializableMessages += message

  def wontDeserialize(protoMessage: CoinffeineMessage): Unit =
    undeserializableMessages += protoMessage
}
