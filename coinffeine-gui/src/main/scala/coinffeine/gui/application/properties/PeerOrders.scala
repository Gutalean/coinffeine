package coinffeine.gui.application.properties

import scala.concurrent.ExecutionContext
import scalafx.collections.ObservableBuffer

import coinffeine.peer.api.CoinffeineOperations

class PeerOrders(operations: CoinffeineOperations,
                 executor: ExecutionContext) extends ObservableBuffer[MutableOrderProperties] {

  implicit val exec = executor

  operations.orders.onNewValue { (id, order) =>
    find(_.idProperty.value == id) match {
      case Some(orderProp) => orderProp.update(order)
      case None => this += new MutableOrderProperties(order)
    }
  }
}
