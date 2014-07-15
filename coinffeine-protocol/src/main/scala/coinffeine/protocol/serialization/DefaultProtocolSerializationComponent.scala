package coinffeine.protocol.serialization

import coinffeine.model.bitcoin.NetworkComponent

trait DefaultProtocolSerializationComponent extends ProtocolSerializationComponent {
  this: NetworkComponent =>

  override def protocolSerialization: ProtocolSerialization =
    new DefaultProtocolSerialization(new TransactionSerialization(network))
}
