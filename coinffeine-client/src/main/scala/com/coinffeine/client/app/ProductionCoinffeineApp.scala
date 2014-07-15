package com.coinffeine.client.app

import coinffeine.model.bitcoin.MainNetComponent
import coinffeine.protocol.gateway.protorpc.ProtoRpcMessageGateway
import com.coinffeine.client.peer.{CoinffeinePeerActor, MarketInfoActor}
import com.coinffeine.client.peer.orders.{OrderActor, OrderSupervisor, SubmissionSupervisor}
import com.coinffeine.common.ProtocolConstants
import com.coinffeine.common.config.FileConfigComponent
import coinffeine.protocol.serialization.DefaultProtocolSerializationComponent

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
