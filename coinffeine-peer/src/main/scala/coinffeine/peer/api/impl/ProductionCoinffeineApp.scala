package coinffeine.peer.api.impl

import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.peer.bitcoin._
import coinffeine.peer.config.{ConfigComponent, FileConfigComponent}
import coinffeine.peer.exchange.fake.FakeExchangeActor
import coinffeine.peer.market._
import coinffeine.peer.payment.okpay.OkPayProcessorActor
import coinffeine.peer.{CoinffeinePeerActor, ProtocolConstants}
import coinffeine.protocol.gateway.protorpc.ProtoRpcMessageGateway
import coinffeine.protocol.serialization.DefaultProtocolSerializationComponent

object ProductionCoinffeineApp {

  trait Component
    extends DefaultCoinffeineApp.Component
    with CoinffeinePeerActor.Component
    with MarketInfoActor.Component
    with ProtocolConstants.DefaultComponent
    with OrderSupervisor.Component
    with SubmissionSupervisor.Component
    with OrderActor.Component
    with FakeExchangeActor.Component
    with WalletActor.Component
    with DummyWalletComponent
    with BitcoinPeerActor.Component
    with MockBlockchainComponent
    with OkPayProcessorActor.Component
    with ProtoRpcMessageGateway.Component
    with DefaultProtocolSerializationComponent
    with FileConfigComponent
    with ConfigComponent
    with NetworkComponent
}
