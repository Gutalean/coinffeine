package coinffeine.peer.events.network

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.order.Order

/** An event published when an order has changed. */
case class OrderChanged(order: Order)

object OrderChanged extends TopicProvider[OrderChanged] {
  override val Topic = "coinffeine.network.order-changed"
}
