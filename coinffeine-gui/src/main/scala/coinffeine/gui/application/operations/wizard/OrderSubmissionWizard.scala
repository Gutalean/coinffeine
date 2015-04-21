package coinffeine.gui.application.operations.wizard

import scalafx.beans.property.ObjectProperty

import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.gui.wizard.Wizard
import coinffeine.model.currency.{Euro, CurrencyAmount, Bitcoin}
import coinffeine.model.market.{OrderPrice, OrderType}
import coinffeine.peer.api.MarketStats

class OrderSubmissionWizard(
    marketStats: MarketStats) extends Wizard[OrderSubmissionWizard.CollectedData](
  steps = Seq(
    new OrderTypeSelectionStep,
    new OrderAmountsStep(marketStats),
    new OrderConfirmationStep
  ),
  initialData = new OrderSubmissionWizard.CollectedData(),
  wizardTitle = "",
  additionalStyles = Seq(Stylesheets.Operations)
)

object OrderSubmissionWizard {

  class CollectedData {

    val orderType: ObjectProperty[OrderType] = new ObjectProperty(this, "orderType")

    val bitcoinAmount: ObjectProperty[CurrencyAmount[Bitcoin.type]] =
      new ObjectProperty(this, "bitcoinAmount")

    val price: ObjectProperty[OrderPrice[Euro.type]] = new ObjectProperty(this, "price")
  }
}
