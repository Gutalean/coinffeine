package com.coinffeine.common.protocol.serialization

import coinffeine.model.network.PeerId
import com.coinffeine.common.protocol.messages.PublicMessage
import com.coinffeine.common.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

trait ProtocolSerialization {
  def fromProtobuf(protoMessage: CoinffeineMessage): (PublicMessage, PeerId)
  def toProtobuf(message: PublicMessage, id: PeerId): CoinffeineMessage
}
