package coinffeine.gui.application.properties

import scala.concurrent.ExecutionContext
import scalafx.collections.ObservableBuffer

import coinffeine.peer.api.CoinffeineNetwork

class PeerOrders(coinffeineNetwork: CoinffeineNetwork,
                 executor: ExecutionContext) extends ObservableBuffer[MutableOrderProperties] {

  implicit val exec = executor

  coinffeineNetwork.orders.onNewValue { (id, order) =>
    find(_.idProperty.value == id) match {
      case Some(orderProp) => orderProp.update(order)
      case None => this += new MutableOrderProperties(order)
    }
  }
}
