package coinffeine.peer.api.impl

import coinffeine.model.bitcoin.{PeerGroupComponent, NetworkComponent}
import coinffeine.peer.bitcoin._
import coinffeine.peer.config.{ConfigComponent, FileConfigComponent}
import coinffeine.peer.exchange.fake.FakeExchangeActor
import coinffeine.peer.{CoinffeinePeerActor, ProtocolConstants}
import coinffeine.protocol.gateway.proto.ProtoMessageGateway
import coinffeine.protocol.serialization.DefaultProtocolSerializationComponent

object ProductionCoinffeineApp {

  trait Component
    extends DefaultCoinffeineApp.Component
      with CoinffeinePeerActor.Component
      with ProtocolConstants.DefaultComponent
      with FakeExchangeActor.Component
      with DummyPrivateKeysComponent
      with BitcoinPeerActor.Component
      with MockBlockchainComponent
      with ProtoMessageGateway.Component
      with DefaultProtocolSerializationComponent
      with FileConfigComponent {
    this: NetworkComponent with PeerGroupComponent =>
  }
}
