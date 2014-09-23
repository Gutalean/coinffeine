package coinffeine.model.market

import coinffeine.model.currency.{CurrencyAmount, BitcoinAmount, FiatCurrency}

/** Funds required to be blocked */
case class RequiredFunds[C <: FiatCurrency](bitcoin: BitcoinAmount, fiat: CurrencyAmount[C]) {
  require(bitcoin.isPositive, s"At least some bitcoin should be required ($bitcoin given)")
  require(!fiat.isNegative, s"Cannot require a negative amount of fiat ($fiat given)")
}
