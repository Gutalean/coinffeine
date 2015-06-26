package coinffeine.headless.commands

import scala.concurrent.Future

import coinffeine.common.properties.PropertyMap
import coinffeine.model.order.{Order, OrderId, OrderRequest}
import coinffeine.peer.api.CoinffeineOperations

class DummyCoinffeineOperations extends CoinffeineOperations {
  override def cancelOrder(order: OrderId): Unit = {}
  override def submitOrder(request: OrderRequest) =
    Future.successful(request.create())
  override val orders: PropertyMap[OrderId, Order] = null
}
