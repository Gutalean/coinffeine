package coinffeine.protocol.serialization

import scalaz.Validation

import akka.util.ByteString

import coinffeine.protocol.Version
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}

trait ProtocolSerialization {
  def deserialize(bytes: ByteString): ProtocolSerialization.Deserialization
  def serialize(message: CoinffeineMessage): ProtocolSerialization.Serialization
}

object ProtocolSerialization {

  type Deserialization = Validation[DeserializationError, CoinffeineMessage]

  sealed trait DeserializationError
  case class InvalidProtocolBuffer(description: String) extends DeserializationError
  case class IncompatibleVersion(actual: Version, expected: Version) extends DeserializationError
  case object EmptyPayload extends DeserializationError
  case class MultiplePayloads(fields: Set[String]) extends DeserializationError
  case class UnsupportedProtobufMessage(fieldName: String) extends DeserializationError
  case class MissingField(fieldName: String) extends DeserializationError

  type Serialization = Validation[SerializationError, ByteString]

  sealed trait SerializationError
  case class UnsupportedMessageClass(messageClass: Class[_ <: PublicMessage])
    extends SerializationError
}
