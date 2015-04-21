package coinffeine.gui.application.operations.wizard

import scalafx.scene.control.{Label, ToggleGroup}
import scalafx.scene.layout.{HBox, VBox}

import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard.CollectedData
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, GlyphToggle}
import coinffeine.gui.wizard.StepPane
import coinffeine.model.market.{Ask, Bid, OrderType}

class OrderTypeSelectionStep extends StepPane[OrderSubmissionWizard.CollectedData] {

  override def bindTo(dataBinding: CollectedData) = {
    dataBinding.orderType <== Buttons.group.selectedToggle.delegate.map(
      t => Option(t).map(_.getUserData.asInstanceOf[OrderType]).orNull)
  }

  override val icon = GlyphIcon.ExchangeTypes

  styleClass += "order-type-sel"

  val question = new Label("Choose the type of order you want to perform") {
    styleClass += "question"
  }

  object Buttons extends HBox {
    styleClass += "options"
    val group = new ToggleGroup()
    val buy = new GlyphToggle("Bid (Buy)") {
      icon = GlyphIcon.Buy
      toggleGroup = group
      toggle.userData = Bid
    }
    val sell = new GlyphToggle("Ask (Sell)") {
      icon = GlyphIcon.Sell
      toggleGroup = group
      toggle.userData = Ask
    }

    content = Seq(buy, sell)
  }

  content = new VBox {
    content = Seq(question, Buttons)
  }
  canContinue <== Buttons.group.selectedToggle.delegate.mapToBool(_ != null)
}
