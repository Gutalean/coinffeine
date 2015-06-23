package coinffeine.gui.application.operations

import coinffeine.model.currency.{Bitcoin, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.{ExchangeStatus, Exchange}
import coinffeine.model.order.Price

object WeightedAveragePrice {

  def average[C <: FiatCurrency](exchanges: Seq[Exchange[C]]): Option[Price[C]] = {

    case class WeightedContribution(bitcoin: Bitcoin.Amount, fiat: CurrencyAmount[C]) {

      def +(other: WeightedContribution) = copy(
        bitcoin = bitcoin + other.bitcoin,
        fiat = fiat + other.fiat
      )

      def toPrice: Price[C] = Price.whenExchanging(bitcoin, fiat)
    }

    def measureContribution(exchange: Exchange[C]): WeightedContribution = {
      exchange.status match {
        case ExchangeStatus.Failed(_) => measureProgressUntilFailing(exchange)
        case _ => measureTotalAmounts(exchange)
      }
    }

    def measureProgressUntilFailing(exchange: Exchange[C]): WeightedContribution = {
      val bitcoinAmount = exchange.role.select(exchange.progress.bitcoinsTransferred)
      val totalFiatAmount = exchange.role.select(exchange.exchangedFiat)
      val proportionalFiatAmount = scaleFiatAmount(totalFiatAmount, completionProportion(exchange))
      WeightedContribution(bitcoinAmount, proportionalFiatAmount)
    }

    def completionProportion(exchange: Exchange[C]): BigDecimal =
      exchange.role.select(exchange.progress.bitcoinsTransferred).value /
        exchange.role.select(exchange.exchangedBitcoin).value

    def scaleFiatAmount(amount: CurrencyAmount[C], proportion: BigDecimal) = {
      CurrencyAmount.closestAmount(amount.value * proportion, amount.currency)
    }

    def measureTotalAmounts(exchange: Exchange[C]): WeightedContribution = WeightedContribution(
      exchange.role.select(exchange.exchangedBitcoin),
      exchange.role.select(exchange.exchangedFiat)
    )

    exchanges.map(measureContribution).reduceLeftOption(_ + _).map(_.toPrice)
  }
}
