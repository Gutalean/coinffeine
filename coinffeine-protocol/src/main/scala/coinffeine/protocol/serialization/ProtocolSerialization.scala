package coinffeine.protocol.serialization

import scalaz.Validation

import akka.util.ByteString

import coinffeine.protocol.Version
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}

trait ProtocolSerialization {
  def deserialize(bytes: ByteString): ProtocolSerialization.Deserialization
  def toProtobuf(message: CoinffeineMessage): proto.CoinffeineMessage
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
}
