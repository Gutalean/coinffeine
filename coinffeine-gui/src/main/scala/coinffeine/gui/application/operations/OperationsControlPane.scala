package coinffeine.gui.application.operations

import scala.concurrent.duration._
import scala.util.Try
import scalafx.Includes._
import scalafx.scene.control._
import scalafx.scene.layout._

import coinffeine.gui.application.operations.validation.DefaultOrderValidation
import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.PollingBean
import coinffeine.gui.scene.styles.{ButtonStyles, PaneStyles, TextStyles}
import coinffeine.gui.util.FxExecutor
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.order.{OrderRequest, Price}
import coinffeine.peer.api.CoinffeineApp
import coinffeine.protocol.messages.brokerage.Quote

private class OperationsControlPane(app: CoinffeineApp, market: Market)
    extends VBox with PaneStyles.Centered {

  id = "operations-control-pane"

  private val validation = new DefaultOrderValidation(app)

  private val bitcoinPrice = new HBox() {
    styleClass += "btc-price"

    private val currentPrice = PollingBean(OperationsControlPane.BitcoinPricePollingInterval) {
      implicit val executor = FxExecutor.asContext
      app.marketStats.currentQuote(market).map(OperationsControlPane.summarize)
    }

    val prelude = new Label("1 BTC = ")

    val amount = new Label with TextStyles.CurrencyAmount {
      text <== currentPrice.map {
        case Some(Some(price)) => price.of(1.BTC).format(Currency.NoSymbol)
        case _ => market.currency.formatMissingAmount(Currency.NoSymbol)
      }.toStr
    }
    val symbol = new Label(market.currency.toString) with TextStyles.CurrencySymbol

    children = Seq(prelude, amount, symbol)
  }

  private val newOrderButton = new Button("New order") with ButtonStyles.Action {
    onAction = submitNewOrder _
    disable <== paymentProcessorIsReady.not()
  }

  private def submitNewOrder(): Unit = {
    val wizard = new OrderSubmissionWizard(
      market, app.marketStats, app.utils.exchangeAmountsCalculator, validation)
    Try(wizard.run(Option(delegate.getScene.getWindow))).foreach { data =>
      val request = OrderRequest(data.orderType.value, data.bitcoinAmount.value, data.price.value)
      app.operations.submitOrder(request)
    }
  }

  private def paymentProcessorIsReady = app.paymentProcessor.balances.mapToBoolean(_.status.isFresh)

  children = Seq(bitcoinPrice, newOrderButton)
}

object OperationsControlPane {
  private val BitcoinPricePollingInterval = 10.seconds

  /** Summarizes a full quote into a simple price or None if there is no price information at all */
  def summarize(quote: Quote): Option[Price] =
    quote.lastPrice orElse summarize(quote.spread)

  private def summarize(spread: Spread): Option[Price] = {
    val spreadAverage = for {
      bid <- spread.highestBid
      ask <- spread.lowestAsk
    } yield bid.averageWith(ask)

    spreadAverage orElse spread.highestBid orElse spread.lowestAsk
  }
}
