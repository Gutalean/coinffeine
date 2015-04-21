package coinffeine.gui.application.operations.wizard

import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.Label
import scalafx.scene.layout.VBox

import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard.CollectedData
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphLabel, GlyphIcon}
import coinffeine.gui.wizard.StepPane
import coinffeine.model.market.{Ask, Bid, LimitPrice, MarketPrice}

class OrderConfirmationStep extends StepPane[OrderSubmissionWizard.CollectedData] {

  private val summary = new Label { styleClass += "summary" }

  private val orderTypeIcon = new GlyphLabel

  override def bindTo(data: ObjectProperty[CollectedData]) = {
    summary.text <== data.value.orderType.delegate.zip(
      data.value.bitcoinAmount, data.value.price) { (orderType, amount, orderPrice) =>
      val verb = orderType match {
        case Bid => "buy"
        case Ask => "sell"
      }
      val price = orderPrice match {
        case MarketPrice(_) => "market price"
        case LimitPrice(p) => s"limit price of $p"
      }
      s"You are about to $verb $amount at $price"
    }

    orderTypeIcon.icon <== data.value.orderType.delegate.map {
      case Bid => GlyphIcon.Buy
      case Ask => GlyphIcon.Sell
    }
  }

  override val icon = GlyphIcon.Network

  canContinue.value = true

  content = new VBox {
    styleClass += "order-confirmation"
    content = Seq(summary, orderTypeIcon)
  }
}
