package coinffeine.gui.application.operations

import coinffeine.model.currency._
import coinffeine.model.exchange.{Exchange, ExchangeStatus}
import coinffeine.model.order.Price

object WeightedAveragePrice {

  def average(exchanges: Seq[Exchange]): Option[Price] = {

    case class WeightedContribution(bitcoin: BitcoinAmount, fiat: FiatAmount) {

      def +(other: WeightedContribution) = copy(
        bitcoin = bitcoin + other.bitcoin,
        fiat = fiat + other.fiat
      )

      def toPrice: Price = Price.whenExchanging(bitcoin, fiat)
    }

    def measureContribution(exchange: Exchange): WeightedContribution = {
      exchange.status match {
        case ExchangeStatus.Failed(_) => measureProgressUntilFailing(exchange)
        case _ => measureTotalAmounts(exchange)
      }
    }

    def measureProgressUntilFailing(exchange: Exchange): WeightedContribution = {
      val bitcoinAmount = exchange.role.select(exchange.progress.bitcoinsTransferred)
      val totalFiatAmount = exchange.role.select(exchange.exchangedFiat)
      val proportionalFiatAmount = scaleFiatAmount(totalFiatAmount, completionProportion(exchange))
      WeightedContribution(bitcoinAmount, proportionalFiatAmount)
    }

    def completionProportion(exchange: Exchange): BigDecimal =
      exchange.role.select(exchange.progress.bitcoinsTransferred).value /
        exchange.role.select(exchange.exchangedBitcoin).value

    def scaleFiatAmount(amount: FiatAmount, proportion: BigDecimal) =
      amount.currency.closestAmount(amount.value * proportion)

    def measureTotalAmounts(exchange: Exchange): WeightedContribution = WeightedContribution(
      exchange.role.select(exchange.exchangedBitcoin),
      exchange.role.select(exchange.exchangedFiat)
    )

    exchanges.map(measureContribution).reduceLeftOption(_ + _).map(_.toPrice)
  }
}
