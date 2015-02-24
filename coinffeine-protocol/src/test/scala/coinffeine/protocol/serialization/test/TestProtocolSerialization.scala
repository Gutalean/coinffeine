package coinffeine.protocol.serialization.test

import scala.util.Try
import scalaz.syntax.validation._

import akka.util.ByteString

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}
import coinffeine.protocol.serialization.ProtocolSerialization._
import coinffeine.protocol.serialization._
import coinffeine.protocol.serialization.protobuf.ProtobufProtocolSerialization

class TestProtocolSerialization extends ProtocolSerialization {
  private val underlying = new ProtobufProtocolSerialization(
    new TransactionSerialization(CoinffeineUnitTestNetwork))
  var notSerializableMessages = Set.empty[CoinffeineMessage]
  var notDeserializableMessages = Set.empty[proto.CoinffeineMessage]

  override def serialize(message: CoinffeineMessage): Serialization =
    if (notSerializableMessages.contains(message)) cannotSerialize
    else underlying.serialize(message)

  def toProtobuf(message: CoinffeineMessage) =
    if (notSerializableMessages.contains(message)) cannotSerialize
    else underlying.toProtobuf(message).getOrElse(cannotSerialize)

  private def cannotSerialize = throw new scala.IllegalArgumentException("Cannot serialize")

  override def deserialize(bytes: ByteString): Deserialization =
    Try(proto.CoinffeineMessage.parseFrom(bytes.toArray)).map(fromProtobuf)
      .getOrElse(InvalidProtocolBuffer(s"invalid $bytes").failure)

  private def fromProtobuf(protoMessage: proto.CoinffeineMessage) =
    if (notDeserializableMessages.contains(protoMessage))
      throw new IllegalArgumentException("Cannot deserialize")
    else underlying.fromProtobuf(protoMessage)

  def wontSerialize(message: PublicMessage): Unit = {
    notSerializableMessages += Payload(message)
  }

  def wontDeserialize(protoMessage: proto.CoinffeineMessage): Unit =
    notDeserializableMessages += protoMessage
}
