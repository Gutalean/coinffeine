package coinffeine.peer.api.impl

import coinffeine.model.bitcoin.MainNetComponent
import coinffeine.peer.bitcoin.{DummyWalletComponent, MockBlockchainComponent, WalletActor}
import coinffeine.peer.config.{ConfigComponent, FileConfigComponent}
import coinffeine.peer.market._
import coinffeine.peer.payment.okpay.OKPayProcessorActor
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
    with OrderActor.Component
    with WalletActor.Component
    with DummyWalletComponent
    with MockBlockchainComponent
    with OKPayProcessorActor.Component
    with SubmissionSupervisor.Component
    with ProtoRpcMessageGateway.Component
    with DefaultProtocolSerializationComponent
    with MainNetComponent
    with FileConfigComponent
    with ConfigComponent
}
