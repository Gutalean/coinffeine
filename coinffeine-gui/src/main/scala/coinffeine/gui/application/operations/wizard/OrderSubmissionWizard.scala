package coinffeine.gui.application.operations.wizard

import scalafx.beans.property.ObjectProperty

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard.CollectedData
import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.gui.wizard.Wizard
import coinffeine.model.currency.{Bitcoin, CurrencyAmount, Euro}
import coinffeine.model.market.{OrderPrice, OrderType}
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.api.MarketStats

class OrderSubmissionWizard private (
    marketStats: MarketStats,
    amountsCalculator: AmountsCalculator,
    validation: OrderValidation,
    data: CollectedData) extends Wizard[CollectedData](
  steps = Seq(
    new OrderTypeSelectionStep(data),
    new OrderAmountsStep(marketStats, amountsCalculator, data, validation),
    new OrderConfirmationStep(data)
  ),
  data = data,
  additionalStyles = Seq(Stylesheets.Operations)) {

  def this(marketStats: MarketStats,
           amountsCalculator: AmountsCalculator,
           validation: OrderValidation) =
    this(marketStats, amountsCalculator, validation, new CollectedData)
}

object OrderSubmissionWizard {

  class CollectedData {

    val orderType: ObjectProperty[OrderType] = new ObjectProperty(this, "orderType")

    val bitcoinAmount: ObjectProperty[CurrencyAmount[Bitcoin.type]] =
      new ObjectProperty(this, "bitcoinAmount")

    val price: ObjectProperty[OrderPrice[Euro.type]] = new ObjectProperty(this, "price")
  }
}
