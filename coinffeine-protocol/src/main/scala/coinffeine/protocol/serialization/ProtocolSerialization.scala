package coinffeine.protocol.serialization

import scalaz.Validation

import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}

trait ProtocolSerialization {
  def fromProtobuf(protoMessage: proto.CoinffeineMessage): ProtocolSerialization.Deserialization
  def toProtobuf(message: CoinffeineMessage): proto.CoinffeineMessage
}

object ProtocolSerialization {

  type Deserialization = Validation[DeserializationError, CoinffeineMessage]
  sealed trait DeserializationError

  case class ProtocolVersionException(msg: String,
                                      cause: Throwable = null) extends Exception(msg, cause)
}
