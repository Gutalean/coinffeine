package coinffeine.model.operations

import coinffeine.common.properties.PropertyMap
import coinffeine.model.exchange._
import coinffeine.model.order._

trait OperationsProperties {
  val orders: PropertyMap[OrderId, Order]

  def exchanges: Set[Exchange] = orders.values.toSet[Order].flatMap(
    order => order.exchanges.values.toSet[Exchange])
}
