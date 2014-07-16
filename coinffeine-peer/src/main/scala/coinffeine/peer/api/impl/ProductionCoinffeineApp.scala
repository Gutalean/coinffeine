package coinffeine.peer.api.impl

import coinffeine.model.bitcoin.MainNetComponent
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.market._
import coinffeine.protocol.gateway.protorpc.ProtoRpcMessageGateway
import coinffeine.protocol.serialization.DefaultProtocolSerializationComponent
import com.coinffeine.common.ProtocolConstants
import com.coinffeine.common.config.FileConfigComponent

object ProductionCoinffeineApp {

  trait Component
    extends DefaultCoinffeineApp.Component
    with CoinffeinePeerActor.Component
    with MarketInfoActor.Component
    with ProtocolConstants.DefaultComponent
    with OrderSupervisor.Component
    with OrderActor.Component
    with SubmissionSupervisor.Component
    with ProtoRpcMessageGateway.Component
    with DefaultProtocolSerializationComponent
    with MainNetComponent
    with FileConfigComponent
}
