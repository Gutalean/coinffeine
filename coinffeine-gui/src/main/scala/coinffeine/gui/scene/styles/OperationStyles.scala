package coinffeine.gui.scene.styles

import coinffeine.model.order._

object OperationStyles {

  def stylesFor(order: Order): Seq[String] = {
    val statusStyle = order.status match {
      case OrderStatus.Completed => "completed"
      case OrderStatus.NotStarted | OrderStatus.InProgress => "running"
      case _ => "failed"
    }
    val orderTypeStyle = order.orderType match {
      case Bid => "buy"
      case Ask => "sell"
    }
    Seq(statusStyle, orderTypeStyle)
  }
}
