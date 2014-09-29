package coinffeine.gui.application.properties

import scalafx.collections.ObservableBuffer

import coinffeine.peer.api.CoinffeineNetwork

class PeerOrders(coinffeineNetwork: CoinffeineNetwork) extends ObservableBuffer[OrderProperties] {

  import coinffeine.gui.util.FxExecutor.asContext

  coinffeineNetwork.orders.onNewValue { (id, order) =>
    find(_.orderIdProperty.value == id) match {
      case Some(orderProp) => orderProp.update(order)
      case None => this += new OrderProperties(order)
    }
  }
}
