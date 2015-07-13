package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.ActorRef

import coinffeine.common.akka.AskPattern
import coinffeine.common.properties.PropertyMap
import coinffeine.model.operations.OperationsProperties
import coinffeine.model.order._
import coinffeine.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, OrderOpened}
import coinffeine.peer.api.CoinffeineOperations

class DefaultCoinffeineOperations(properties: OperationsProperties,
                                  override val peer: ActorRef)
  extends CoinffeineOperations with PeerActorWrapper {

  override val orders: PropertyMap[OrderId, Order] = properties.orders

  override def submitOrder(order: OrderRequest) =
    AskPattern(peer, OpenOrder(order))
      .withReply[OrderOpened]()
      .map(_.order.asInstanceOf[ActiveOrder])

  override def cancelOrder(order: OrderId): Unit = {
    peer ! CancelOrder(order)
  }
}
