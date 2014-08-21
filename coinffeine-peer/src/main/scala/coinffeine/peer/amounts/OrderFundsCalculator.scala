package coinffeine.peer.amounts

import coinffeine.model.currency._
import coinffeine.model.market.Order

trait OrderFundsCalculator {

  def calculateFunds[C](order: Order[FiatCurrency]): (CurrencyAmount[FiatCurrency], BitcoinAmount)
}
