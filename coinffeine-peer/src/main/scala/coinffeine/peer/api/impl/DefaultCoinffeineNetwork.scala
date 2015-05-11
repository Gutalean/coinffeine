package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.ActorRef

import coinffeine.common.akka.AskPattern
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market._
import coinffeine.model.network.{CoinffeineNetworkProperties, PeerId}
import coinffeine.model.order.{OrderRequest, ActiveOrder, OrderId, AnyCurrencyOrder}
import coinffeine.model.properties.{Property, PropertyMap}
import coinffeine.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, OrderOpened}
import coinffeine.peer.api.CoinffeineNetwork

private[impl] class DefaultCoinffeineNetwork(
    properties: CoinffeineNetworkProperties,
    override val peer: ActorRef) extends CoinffeineNetwork with PeerActorWrapper {

  override val activePeers: Property[Int] = properties.activePeers
  override val brokerId: Property[Option[PeerId]] = properties.brokerId
  override val orders: PropertyMap[OrderId, AnyCurrencyOrder] = properties.orders

  override def submitOrder[C <: FiatCurrency](order: OrderRequest[C]) =
    AskPattern(peer, OpenOrder(order))
      .withReply[OrderOpened]()
      .map(_.order.asInstanceOf[ActiveOrder[C]])

  override def cancelOrder(order: OrderId): Unit = {
    peer ! CancelOrder(order)
  }
}
