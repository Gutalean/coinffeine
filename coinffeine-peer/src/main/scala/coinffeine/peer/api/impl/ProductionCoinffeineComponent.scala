package coinffeine.peer.api.impl

import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.bitcoin._
import coinffeine.peer.bitcoin.platform.DefaultBitcoinPlatform
import coinffeine.peer.config.user.UserFileConfigComponent
import coinffeine.peer.exchange.DefaultExchangeActor
import coinffeine.peer.exchange.protocol.impl.DefaultExchangeProtocol
import coinffeine.peer.properties.DefaultCoinffeinePropertiesComponent
import coinffeine.peer.{CoinffeinePeerActor, ProtocolConstants}
import coinffeine.protocol.gateway.overlay.OverlayMessageGateway
import coinffeine.protocol.serialization.DefaultProtocolSerializationComponent

trait ProductionCoinffeineComponent
  extends DefaultCoinffeineApp.Component
    with CoinffeinePeerActor.Component
    with DefaultAmountsComponent
    with ProtocolConstants.DefaultComponent
    with DefaultExchangeActor.Component
    with DefaultExchangeProtocol.Component
    with BitcoinPeerActor.Component
    with DefaultBitcoinPlatform.Component
    with OverlayMessageGateway.Component
    with DefaultProtocolSerializationComponent
    with UserFileConfigComponent
    with DefaultCoinffeinePropertiesComponent
