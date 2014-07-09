package com.coinffeine.common.protocol.serialization

import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.protocol.messages.PublicMessage
import com.coinffeine.common.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

trait ProtocolSerialization {
  def fromProtobuf(protoMessage: CoinffeineMessage): (PublicMessage, PeerId)
  def toProtobuf(message: PublicMessage, id: PeerId): CoinffeineMessage
}
