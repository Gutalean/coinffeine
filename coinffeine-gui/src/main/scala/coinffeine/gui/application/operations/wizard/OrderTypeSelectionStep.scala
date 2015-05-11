package coinffeine.gui.application.operations.wizard

import scalafx.scene.control.{Label, ToggleGroup}
import scalafx.scene.layout.{HBox, VBox}

import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard.CollectedData
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, GlyphToggle, SupportWidget}
import coinffeine.gui.wizard.{StepPane, StepPaneEvent}
import coinffeine.model.order.{Ask, Bid, OrderType}

class OrderTypeSelectionStep(
    data: CollectedData) extends StepPane[OrderSubmissionWizard.CollectedData] {

  override val icon = GlyphIcon.ExchangeTypes

  styleClass += "order-type-sel"

  val question = new HBox {
    styleClass += "question"
    children = Seq(
      new Label("Choose the type of order\nyou want to perform"),
      new SupportWidget("create-order")
    )
  }

  object Buttons extends HBox {
    styleClass += "options"
    val group = new ToggleGroup()
    val buy = new GlyphToggle("Bid (Buy)") {
      styleClass += "buy"
      icon = GlyphIcon.Buy
      toggleGroup = group
      toggle.userData = Bid
    }
    val sell = new GlyphToggle("Ask (Sell)") {
      styleClass += "sell"
      icon = GlyphIcon.Sell
      toggleGroup = group
      toggle.userData = Ask
    }

    children = Seq(buy, sell)
  }

  onActivation = { e: StepPaneEvent  =>
    data.orderType <== Buttons.group.selectedToggle.delegate.map(
      t => Option(t).map(_.getUserData.asInstanceOf[OrderType]).orNull)

    canContinue <== Buttons.group.selectedToggle.delegate.mapToBool(_ != null)
  }

  children = new VBox(question, Buttons)
}
