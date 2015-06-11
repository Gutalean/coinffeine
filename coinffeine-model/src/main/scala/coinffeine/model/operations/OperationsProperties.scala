package coinffeine.model.operations

import coinffeine.common.properties.PropertyMap
import coinffeine.model.exchange._
import coinffeine.model.order._

trait OperationsProperties {
  val orders: PropertyMap[OrderId, AnyCurrencyOrder]

  def exchanges: Set[AnyExchange] = orders.values.toSet[AnyCurrencyOrder].flatMap(
    order => order.exchanges.values.toSet[AnyExchange])
}
