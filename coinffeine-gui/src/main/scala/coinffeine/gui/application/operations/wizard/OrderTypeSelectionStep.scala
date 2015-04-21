package coinffeine.gui.application.operations.wizard

import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.{ToggleGroup, Label}
import scalafx.scene.layout.{VBox, HBox}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphToggle, GlyphIcon}
import coinffeine.gui.wizard.StepPane
import coinffeine.model.market.{Ask, Bid, OrderType}

class OrderTypeSelectionStep extends StepPane[OrderSubmissionWizard.CollectedData] {

  override def bindTo(dataBinding: ObjectProperty[OrderSubmissionWizard.CollectedData]) = {
    dataBinding.value.orderType <== Buttons.group.selectedToggle.delegate.map(
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
