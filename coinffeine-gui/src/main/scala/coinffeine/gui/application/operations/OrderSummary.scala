package coinffeine.gui.application.operations

import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.control.Label
import scalafx.scene.layout.HBox

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.styles.NodeStyles
import coinffeine.model.market._

class OrderSummary(orderProperty: ReadOnlyObjectProperty[AnyCurrencyOrder]) extends HBox(0) {
  styleClass += "summary"

  content = Seq(
    new Label {
      text <== stringBinding(summarize)
    },
    new Label with NodeStyles.Poppable {
      text <== stringBinding(_.amounts.exchanged.toString)
      popOverContent = new Label {
        text <== stringBinding(formatFiatAmount)
      }
    }
  )

  private def stringBinding(pred: AnyCurrencyOrder => String) =
    orderProperty.delegate.mapToString(pred)

  private def summarize(order: AnyCurrencyOrder): String = {
    val action = order.status match {
      case CancelledOrder => OrderSummary.Cancelled
      case active if active.isActive => OrderSummary.InProgress
      case _ => OrderSummary.Completed
    }
    OrderSummary.Texts(action)(order.orderType)
  }

  private def formatFiatAmount(order: AnyCurrencyOrder): String = order.price match {
    case MarketPrice(currency) => s"at $currency market price"
    case LimitPrice(price) => price.of(order.amount).format
  }
}

private object OrderSummary {
  sealed trait Action
  case object InProgress extends Action
  case object Completed extends Action
  case object Cancelled extends Action

  val Texts: Map[Action, Map[OrderType, String]] = Map(
    InProgress -> Map(
      Bid -> "You are buying ",
      Ask -> "You are selling "
    ),
    Completed -> Map(
      Bid -> "You have bought ",
      Ask -> "You have sold "
    ),
    Cancelled -> Map(
      Bid -> "You have cancelled your buy order of ",
      Ask -> "You have cancelled your sell order of "
    )
  )
}
