package coinffeine.peer.api.impl

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext.Implicits.global

import coinffeine.common.akka.AskPattern
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{ActiveOrder, AnyCurrencyActiveOrder, OrderId, OrderRequest}
import coinffeine.model.network.{CoinffeineNetworkProperties, PeerId}
import coinffeine.model.properties.{Property, PropertyMap}
import coinffeine.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, OrderOpened}
import coinffeine.peer.api.CoinffeineNetwork

private[impl] class DefaultCoinffeineNetwork(
    properties: CoinffeineNetworkProperties,
    override val peer: ActorRef) extends CoinffeineNetwork with PeerActorWrapper {

  override val activePeers: Property[Int] = properties.activePeers
  override val brokerId: Property[Option[PeerId]] = properties.brokerId
  override val orders: PropertyMap[OrderId, AnyCurrencyActiveOrder] = properties.orders

  override def submitOrder[C <: FiatCurrency](order: OrderRequest[C]): ActiveOrder[C] = {
    val result = await(AskPattern(peer, OpenOrder(order)).withReply[OrderOpened]())
    result.order.asInstanceOf[ActiveOrder[C]]
  }

  override def cancelOrder(order: OrderId): Unit = {
    peer ! CancelOrder(order)
  }
}
