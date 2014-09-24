package coinffeine.model.network

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.AnyStateExchange
import coinffeine.model.market.{Order, OrderId}
import coinffeine.model.properties.{MutablePropertyMap, PropertyMap, MutableProperty, Property}

trait CoinffeineNetworkProperties {
  type AnyOrder = Order[_ <: FiatCurrency]

  val activePeers: Property[Int]
  val brokerId: Property[Option[PeerId]]
  val orders: PropertyMap[OrderId, AnyOrder]

  def isConnected: Boolean = activePeers.get > 0 && brokerId.get.isDefined
  def exchanges: Set[AnyStateExchange[_]] =
    orders.values.toSet[AnyOrder].flatMap(order => order.exchanges.values.toSet[AnyStateExchange[_]])
}

class MutableCoinffeineNetworkProperties extends CoinffeineNetworkProperties {

  override val activePeers: MutableProperty[Int] = new MutableProperty(0)
  override val brokerId: MutableProperty[Option[PeerId]] = new MutableProperty(None)
  override val orders: MutablePropertyMap[OrderId, AnyOrder] = new MutablePropertyMap
}

object MutableCoinffeineNetworkProperties {

  trait Component {
    def coinffeineNetworkProperties: MutableCoinffeineNetworkProperties
  }
}
