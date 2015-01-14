package coinffeine.protocol.serialization

import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

trait ProtocolSerialization {
  def fromProtobuf(protoMessage: CoinffeineMessage): PublicMessage
  def toProtobuf(message: PublicMessage): CoinffeineMessage
}

object ProtocolSerialization {

  case class ProtocolVersionException(msg: String,
                                      cause: Throwable = null) extends Exception(msg, cause)
}
