package coinffeine.headless.commands

import scala.concurrent.Future

import coinffeine.common.properties.{PropertyMap, Property}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.PeerId
import coinffeine.model.order.{OrderRequest, OrderId, AnyCurrencyOrder}
import coinffeine.peer.api.CoinffeineNetwork

class DummyCoinffeineNetwork extends CoinffeineNetwork {
  override def cancelOrder(order: OrderId): Unit = {}
  override def submitOrder[C <: FiatCurrency](request: OrderRequest[C]) =
    Future.successful(request.create())
  override val activePeers: Property[Int] = null
  override val brokerId: Property[Option[PeerId]] = null
  override val orders: PropertyMap[OrderId, AnyCurrencyOrder] = null
}
