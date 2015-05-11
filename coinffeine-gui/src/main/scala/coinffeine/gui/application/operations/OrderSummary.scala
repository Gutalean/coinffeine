package coinffeine.gui.application.operations

import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.layout.HBox

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.styles.{NodeStyles, TextStyles}
import coinffeine.model.currency.Currency
import coinffeine.model.market._

class OrderSummary(orderProperty: ReadOnlyObjectProperty[AnyCurrencyOrder]) extends HBox(0) {
  styleClass += "summary"

  children = Seq(
    new Label {
      text <== stringBinding(summarize)
    },
    new Label with NodeStyles.Poppable {
      text <== stringBinding(_.amount.toString)
      popOverContent = fiatAmountNode()
    }
  )

  private def fiatAmountNode(): Node = {
    val order = orderProperty.value
    order.price match {
      case MarketPrice(currency) => new Label(s"at $currency market price")

      case LimitPrice(price) =>
        val amount = price.of(order.amount)
        new HBox {
          children = Seq(
            new Label(amount.format(Currency.NoSymbol)) with TextStyles.CurrencyAmount,
            new Label(amount.currency.toString) with TextStyles.CurrencySymbol
          )
        }
    }
  }

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
      Bid -> "You cancelled your buy of ",
      Ask -> "You cancelled your sell of "
    )
  )
}
