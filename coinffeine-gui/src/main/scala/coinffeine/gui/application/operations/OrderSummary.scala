package coinffeine.gui.application.operations

import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.control.{Label, Tooltip}
import scalafx.scene.layout.HBox

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.styles.NodeStyles
import coinffeine.model.market._

class OrderSummary(orderProperty: ReadOnlyObjectProperty[AnyCurrencyOrder]) extends HBox(0) {
  styleClass += "summary"

  content = Seq(
    new Label with NodeStyles.VExpand {
      text <== orderProperty.delegate.mapToString(summarize)
    },
    new Label with NodeStyles.VExpand {
      styleClass += "poppable"
      text <== orderProperty.delegate.mapToString(_.amounts.exchanged.toString)
    }
  )

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
      Bid -> "You have cancelled your buy order of ",
      Ask -> "You have cancelled your sell order of "
    )
  )
}
