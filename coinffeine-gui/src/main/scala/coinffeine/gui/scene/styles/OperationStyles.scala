package coinffeine.gui.scene.styles

import coinffeine.model.market._

object OperationStyles {

  def stylesFor(order: AnyCurrencyOrder): Seq[String] = {
    val statusStyle = order.status match {
      case CompletedOrder => "completed"
      case NotStartedOrder | InProgressOrder => "running"
      case _ => "failed"
    }
    val orderTypeStyle = order.orderType match {
      case Bid => "buy"
      case Ask => "sell"
    }
    Seq(statusStyle, orderTypeStyle)
  }
}
