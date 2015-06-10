package coinffeine.peer.api.mock

import scala.concurrent.Future

import org.joda.time.DateTime

import coinffeine.common.properties.{MutableProperty, MutablePropertyMap}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.PeerId
import coinffeine.model.order.{AnyCurrencyActiveOrder, AnyCurrencyOrder, OrderId, OrderRequest}
import coinffeine.peer.api.CoinffeineNetwork

class MockCoinffeineNetwork extends CoinffeineNetwork {

  override val activePeers: MutableProperty[Int] = new MutableProperty(0)
  override val brokerId: MutableProperty[Option[PeerId]] = new MutableProperty(None)
  override val orders: MutablePropertyMap[OrderId, AnyCurrencyOrder] = new MutablePropertyMap

  override def cancelOrder(orderId: OrderId): Unit = {
    orders.get(orderId).foreach(order =>
      orders.set(orderId, order.asInstanceOf[AnyCurrencyActiveOrder].cancel(DateTime.now())))
  }

  override def submitOrder[C <: FiatCurrency](request: OrderRequest[C]) = {
    val order = request.create()
    orders.set(order.id, order)
    Future.successful(order)
  }
}
