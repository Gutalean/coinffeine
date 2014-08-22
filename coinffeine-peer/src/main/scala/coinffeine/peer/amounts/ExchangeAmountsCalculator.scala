package coinffeine.peer.amounts

import coinffeine.model.currency._
import coinffeine.model.market.Order

trait ExchangeAmountsCalculator {

  def amountsFor[C](order: Order[FiatCurrency]): (CurrencyAmount[FiatCurrency], BitcoinAmount)
}
