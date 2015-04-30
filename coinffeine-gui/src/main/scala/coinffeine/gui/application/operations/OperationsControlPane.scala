package coinffeine.gui.application.operations

import scala.concurrent.duration._
import scala.util.Try
import scalafx.Includes._
import scalafx.scene.control._
import scalafx.scene.layout._

import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.PollingBean
import coinffeine.gui.scene.styles.{ButtonStyles, PaneStyles, TextStyles}
import coinffeine.gui.util.FxExecutor
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.api.CoinffeineApp
import coinffeine.protocol.messages.brokerage.Quote

private class OperationsControlPane(app: CoinffeineApp) extends VBox with PaneStyles.Centered {
  id = "operations-control-pane"

  val bitcoinPrice = new HBox() {
    styleClass += "btc-price"

    private val currentPrice = PollingBean(OperationsControlPane.BitcoinPricePollingInterval) {
      implicit val executor = FxExecutor.asContext
      app.marketStats.currentQuote(Market(Euro)).map(quote => OperationsControlPane.summarize(quote))
    }

    val prelude = new Label("1 BTC = ")

    val amount = new Label with TextStyles.CurrencyAmount {
      text <== currentPrice.mapToString {
        case Some(Some(p)) => p.of(1.BTC).format(Currency.NoSymbol)
        case _ => CurrencyAmount.formatMissing(Euro, Currency.NoSymbol)
      }
    }
    val symbol = new Label(Euro.toString) with TextStyles.CurrencySymbol

    content = Seq(prelude, amount, symbol)
  }

  val newOrderButton = new Button("New order") with ButtonStyles.Action {
    onAction = submitNewOrder _
  }

  private def submitNewOrder(): Unit = {
    val wizard = new OrderSubmissionWizard(app.marketStats, app.utils.exchangeAmountsCalculator)
    Try(wizard.run(Some(delegate.getScene.getWindow))).foreach { data =>
      val order = Order.random(data.orderType.value, data.bitcoinAmount.value, data.price.value)
      app.network.submitOrder(order)
    }
  }

  content = Seq(bitcoinPrice, newOrderButton)
}

object OperationsControlPane {
  private val BitcoinPricePollingInterval = 10.seconds

  /** Summarizes a full quote into a simple price or None if there is no price information at all */
  def summarize[C <: FiatCurrency](quote: Quote[C]): Option[Price[C]] =
    quote.lastPrice orElse summarize(quote.spread)

  private def summarize[C <: FiatCurrency](spread: Spread[C]): Option[Price[C]] = {
    val spreadAverage = for {
      bid <- spread.highestBid
      ask <- spread.lowestAsk
    } yield bid.averageWith(ask)

    spreadAverage orElse spread.highestBid orElse spread.lowestAsk
  }
}
