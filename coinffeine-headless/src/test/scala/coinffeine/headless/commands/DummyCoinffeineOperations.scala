package coinffeine.headless.commands

import scala.concurrent.Future

import coinffeine.common.properties.PropertyMap
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.order.{AnyCurrencyOrder, OrderId, OrderRequest}
import coinffeine.peer.api.CoinffeineOperations

class DummyCoinffeineOperations extends CoinffeineOperations {
  override def cancelOrder(order: OrderId): Unit = {}
  override def submitOrder[C <: FiatCurrency](request: OrderRequest[C]) =
    Future.successful(request.create())
  override val orders: PropertyMap[OrderId, AnyCurrencyOrder] = null
}
