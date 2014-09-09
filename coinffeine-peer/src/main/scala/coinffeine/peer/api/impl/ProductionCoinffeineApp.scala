package coinffeine.peer.api.impl

import coinffeine.model.bitcoin.{NetworkComponent, PeerGroupComponent}
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.bitcoin._
import coinffeine.peer.config.user.UserFileConfigComponent
import coinffeine.peer.exchange.DefaultExchangeActor
import coinffeine.peer.exchange.protocol.impl.DefaultExchangeProtocol
import coinffeine.peer.{CoinffeinePeerActor, ProtocolConstants}
import coinffeine.protocol.gateway.proto.ProtoMessageGateway
import coinffeine.protocol.serialization.DefaultProtocolSerializationComponent

object ProductionCoinffeineApp {

  trait Component
    extends DefaultCoinffeineApp.Component
      with CoinffeinePeerActor.Component
      with DefaultAmountsComponent
      with ProtocolConstants.DefaultComponent
      with DefaultExchangeActor.Component
      with DefaultExchangeProtocol.Component
      with DummyPrivateKeysComponent
      with BitcoinPeerActor.Component
      with DefaultBitcoinComponents
      with ProtoMessageGateway.Component
      with DefaultProtocolSerializationComponent
      with UserFileConfigComponent {
    this: NetworkComponent with PeerGroupComponent =>
  }
}
