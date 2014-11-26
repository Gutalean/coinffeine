package coinffeine.peer.api.impl

import scala.concurrent.duration._

import akka.actor.ActorRef
import org.slf4j.LoggerFactory

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{AnyCurrencyOrder, Order, OrderId}
import coinffeine.model.network.{CoinffeineNetworkProperties, PeerId}
import coinffeine.model.properties.{Property, PropertyMap}
import coinffeine.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder}
import coinffeine.peer.api.CoinffeineNetwork

private[impl] class DefaultCoinffeineNetwork(
    properties: CoinffeineNetworkProperties,
    override val peer: ActorRef) extends CoinffeineNetwork with PeerActorWrapper {

  override val activePeers: Property[Int] = properties.activePeers
  override val brokerId: Property[Option[PeerId]] = properties.brokerId
  override val orders: PropertyMap[OrderId, AnyCurrencyOrder] = properties.orders

  override def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C] = {
    peer ! OpenOrder(order)
    order
  }

  override def cancelOrder(order: OrderId): Unit = {
    peer ! CancelOrder(order)
  }
}

object DefaultCoinffeineNetwork {
  val ConnectionTimeout = 30.seconds
  val Log = LoggerFactory.getLogger(classOf[DefaultCoinffeineNetwork])
}
