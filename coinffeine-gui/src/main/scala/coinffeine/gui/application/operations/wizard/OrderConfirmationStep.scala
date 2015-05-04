package coinffeine.gui.application.operations.wizard

import scalafx.scene.control.Label
import scalafx.scene.layout.VBox

import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard.CollectedData
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, GlyphLabel}
import coinffeine.gui.wizard.{StepPane, StepPaneEvent}
import coinffeine.model.market._

class OrderConfirmationStep(
    data: CollectedData) extends StepPane[OrderSubmissionWizard.CollectedData] {

  override val icon = GlyphIcon.Network

  private val summary = new Label { styleClass += "summary" }

  private val orderTypeIcon = new GlyphLabel

  onActivation = { _: StepPaneEvent =>
    bindSummaryText()
    bindOrderTypeIcon()
  }

  private def bindSummaryText(): Unit = {
    summary.text <== data.orderType.delegate.zip(
      data.bitcoinAmount, data.price) { (orderType, amount, orderPrice) =>
      val verb = orderType match {
        case Bid => "buy"
        case Ask => "sell"
      }
      val price = orderPrice match {
        case MarketPrice(_) => "market price"
        case LimitPrice(p) => s"a limit price of $p"
      }
      s"You are about to $verb $amount at $price"
    }
  }

  private def bindOrderTypeIcon(): Unit = {
    orderTypeIcon.icon <== data.orderType.delegate.map {
      case Bid => GlyphIcon.Buy
      case Ask => GlyphIcon.Sell
    }
  }

  content = new VBox {
    styleClass += "order-confirmation"
    content = Seq(summary, orderTypeIcon)
  }
}
