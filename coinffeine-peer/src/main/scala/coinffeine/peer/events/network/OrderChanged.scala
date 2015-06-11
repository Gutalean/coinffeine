package coinffeine.peer.events.network

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.order.AnyCurrencyOrder

/** An event published when an order has changed. */
case class OrderChanged(order: AnyCurrencyOrder)

object OrderChanged extends TopicProvider[OrderChanged] {
  override val Topic = "coinffeine.network.order-changed"
}
