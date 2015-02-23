package coinffeine.protocol.serialization

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}

class TestProtocolSerialization extends ProtocolSerialization {
  private val underlying = new DefaultProtocolSerialization(
    new TransactionSerialization(CoinffeineUnitTestNetwork))
  var notSerializableMessages = Set.empty[CoinffeineMessage]
  var notDeserializableMessages = Set.empty[proto.CoinffeineMessage]

  override def toProtobuf(message: CoinffeineMessage) =
    if (notSerializableMessages.contains(message))
      throw new IllegalArgumentException("Cannot serialize")
    else underlying.toProtobuf(message)

  override def fromProtobuf(protoMessage: proto.CoinffeineMessage): CoinffeineMessage =
    if (notDeserializableMessages.contains(protoMessage))
      throw new IllegalArgumentException("Cannot deserialize")
    else underlying.fromProtobuf(protoMessage)

  def wontSerialize(message: PublicMessage): Unit = {
    notSerializableMessages += Payload(message)
  }

  def wontDeserialize(protoMessage: proto.CoinffeineMessage): Unit =
    notDeserializableMessages += protoMessage
}
