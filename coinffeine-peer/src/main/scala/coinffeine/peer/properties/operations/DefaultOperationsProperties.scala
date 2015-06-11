package coinffeine.peer.properties.operations

import akka.actor.ActorSystem

import coinffeine.common.akka.event.EventObservedPropertyMap
import coinffeine.model.operations.OperationsProperties
import coinffeine.model.order.{AnyCurrencyOrder, OrderId}
import coinffeine.peer.events.network.OrderChanged

class DefaultOperationsProperties(implicit system: ActorSystem) extends OperationsProperties {

  override val orders = EventObservedPropertyMap[OrderId, AnyCurrencyOrder](OrderChanged.Topic) {
    case OrderChanged(order) => EventObservedPropertyMap.Put(order.id, order)
  }
}
