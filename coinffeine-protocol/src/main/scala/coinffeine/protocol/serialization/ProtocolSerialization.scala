package coinffeine.protocol.serialization

import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}

trait ProtocolSerialization {
  def fromProtobuf(protoMessage: proto.CoinffeineMessage): CoinffeineMessage
  def toProtobuf(message: CoinffeineMessage): proto.CoinffeineMessage
}

object ProtocolSerialization {

  case class ProtocolVersionException(msg: String,
                                      cause: Throwable = null) extends Exception(msg, cause)
}
