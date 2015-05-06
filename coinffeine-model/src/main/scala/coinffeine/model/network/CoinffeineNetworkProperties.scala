package coinffeine.model.network

import coinffeine.model.exchange.{AnyExchange}
import coinffeine.model.market.{AnyCurrencyActiveOrder, OrderId}
import coinffeine.model.properties.{MutablePropertyMap, PropertyMap, MutableProperty, Property}

trait CoinffeineNetworkProperties {
  val activePeers: Property[Int]
  val brokerId: Property[Option[PeerId]]
  val orders: PropertyMap[OrderId, AnyCurrencyActiveOrder]

  def isConnected: Boolean = activePeers.get > 0 && brokerId.get.isDefined
  def exchanges: Set[AnyExchange] = orders.values.toSet[AnyCurrencyActiveOrder].flatMap(
    order => order.exchanges.values.toSet[AnyExchange])
}

class MutableCoinffeineNetworkProperties extends CoinffeineNetworkProperties {

  override val activePeers: MutableProperty[Int] = new MutableProperty(0)
  override val brokerId: MutableProperty[Option[PeerId]] = new MutableProperty(None)
  override val orders: MutablePropertyMap[OrderId, AnyCurrencyActiveOrder] = new MutablePropertyMap
}

object MutableCoinffeineNetworkProperties {

  trait Component {
    def coinffeineNetworkProperties: MutableCoinffeineNetworkProperties
  }
}
