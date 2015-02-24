package coinffeine.protocol.serialization.protobuf

import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.protocol.serialization.{ProtocolSerialization, ProtocolSerializationComponent, TransactionSerialization}

trait ProtobufProtocolSerializationComponent extends ProtocolSerializationComponent {
  this: NetworkComponent =>

  override def protocolSerialization: ProtocolSerialization =
    new ProtobufProtocolSerialization(new TransactionSerialization(network))
}
