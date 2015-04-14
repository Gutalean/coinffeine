package coinffeine.gui.scene.styles

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.model.market.{Ask, Bid}

object OperationStyles {

  private val BuyStyleClass = "buy"
  private val SellStyleClass = "sell"

  def styleClassFor(order: OrderProperties): String = order.orderTypeProperty.value match {
    case Bid => BuyStyleClass
    case Ask => SellStyleClass
  }
}
