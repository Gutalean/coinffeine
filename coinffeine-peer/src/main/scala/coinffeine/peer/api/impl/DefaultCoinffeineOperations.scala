package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.ActorRef

import coinffeine.common.akka.AskPattern
import coinffeine.common.properties.PropertyMap
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.operations.OperationsProperties
import coinffeine.model.order._
import coinffeine.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, OrderOpened}
import coinffeine.peer.api.CoinffeineOperations

class DefaultCoinffeineOperations(properties: OperationsProperties,
                                  override val peer: ActorRef)
  extends CoinffeineOperations with PeerActorWrapper {

  override val orders: PropertyMap[OrderId, AnyCurrencyOrder] = properties.orders

  override def submitOrder[C <: FiatCurrency](order: OrderRequest[C]) =
    AskPattern(peer, OpenOrder(order))
      .withReply[OrderOpened]()
      .map(_.order.asInstanceOf[ActiveOrder[C]])

  override def cancelOrder(order: OrderId): Unit = {
    peer ! CancelOrder(order)
  }
}
