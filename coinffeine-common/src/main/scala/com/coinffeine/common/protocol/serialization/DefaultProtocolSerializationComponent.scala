package com.coinffeine.common.protocol.serialization

import coinffeine.model.bitcoin.NetworkComponent
import com.coinffeine.common.protocol.ProtocolConstants

trait DefaultProtocolSerializationComponent extends ProtocolSerializationComponent {
  this: NetworkComponent with ProtocolConstants.Component =>

  override def protocolSerialization: ProtocolSerialization =
    new DefaultProtocolSerialization(protocolConstants.version, new TransactionSerialization(network))
}
