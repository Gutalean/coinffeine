package coinffeine.peer.api.impl

import coinffeine.peer.alarms.AlarmReporterActor
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.bitcoin._
import coinffeine.peer.bitcoin.platform.DefaultBitcoinPlatform
import coinffeine.peer.config.user.UserFileConfigComponent
import coinffeine.peer.exchange.DefaultExchangeActor
import coinffeine.peer.exchange.protocol.impl.DefaultExchangeProtocol
import coinffeine.peer.market.orders.archive.h2.H2OrderArchive
import coinffeine.peer.properties.DefaultCoinffeinePropertiesComponent
import coinffeine.peer.{CoinffeinePeerActor, ProtocolConstants}
import coinffeine.protocol.gateway.overlay.OverlayMessageGateway
import coinffeine.protocol.serialization.protobuf.ProtobufProtocolSerializationComponent

trait ProductionCoinffeineComponent
  extends DefaultCoinffeineApp.Component
    with CoinffeinePeerActor.Component
    with DefaultAmountsComponent
    with ProtocolConstants.DefaultComponent
    with AlarmReporterActor.Component
    with DefaultExchangeActor.Component
    with DefaultExchangeProtocol.Component
    with BitcoinPeerActor.Component
    with DefaultBitcoinPlatform.Component
    with OverlayMessageGateway.Component
    with ProtobufProtocolSerializationComponent
    with UserFileConfigComponent
    with DefaultCoinffeinePropertiesComponent
    with H2OrderArchive.Component
