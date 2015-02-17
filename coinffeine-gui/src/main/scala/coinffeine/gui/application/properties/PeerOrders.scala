package coinffeine.gui.application.properties

import scala.concurrent.ExecutionContext
import scalafx.collections.ObservableBuffer

import coinffeine.peer.api.CoinffeineNetwork

class PeerOrders(coinffeineNetwork: CoinffeineNetwork,
                 executor: ExecutionContext) extends ObservableBuffer[OrderProperties] {

  implicit val exec = executor

  coinffeineNetwork.orders.onNewValue { (id, order) =>
    find(_.orderIdProperty.value == id) match {
      case Some(orderProp) => orderProp.update(order)
      case None => this += new OrderProperties(order)
    }
  }
}
