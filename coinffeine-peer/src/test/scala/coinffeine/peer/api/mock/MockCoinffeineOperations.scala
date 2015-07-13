package coinffeine.peer.api.mock

import scala.concurrent.Future

import org.joda.time.DateTime

import coinffeine.common.properties.MutablePropertyMap
import coinffeine.model.order.{ActiveOrder, Order, OrderId, OrderRequest}
import coinffeine.peer.api.CoinffeineOperations

class MockCoinffeineOperations extends CoinffeineOperations {

  override val orders: MutablePropertyMap[OrderId, Order] = new MutablePropertyMap

  override def cancelOrder(orderId: OrderId): Unit = {
    orders.get(orderId).foreach(order =>
      orders.set(orderId, order.asInstanceOf[ActiveOrder].cancel(DateTime.now())))
  }

  override def submitOrder(request: OrderRequest) = {
    val order = request.create()
    orders.set(order.id, order)
    Future.successful(order)
  }
}
