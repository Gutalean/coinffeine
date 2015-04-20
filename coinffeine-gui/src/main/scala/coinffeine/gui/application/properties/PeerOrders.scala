package coinffeine.gui.application.properties

import scala.concurrent.ExecutionContext
import scalafx.collections.ObservableBuffer

import coinffeine.model.market.AnyCurrencyOrder
import coinffeine.peer.api.CoinffeineNetwork

class PeerOrders(coinffeineNetwork: CoinffeineNetwork,
                 executor: ExecutionContext) extends ObservableBuffer[MutableOrderProperties] {

  implicit val exec = executor

  coinffeineNetwork.orders.onNewValue { (id, order) =>
    find(_.idProperty.value == id).fold(insertNewOrder(order))(_.update(order))
  }

  private def insertNewOrder(order: AnyCurrencyOrder): Unit = {
    val index = takeWhile { props =>
      order.lastChange.isAfter(props.orderProperty.value.lastChange)
    }.size
    insert(index, new MutableOrderProperties(order))
  }
}
