package coinffeine.model.market

import coinffeine.model.currency._

/** Funds required to be blocked */
case class RequiredFunds(bitcoin: BitcoinAmount, fiat: FiatAmount) {
  require(bitcoin.isPositive, s"At least some bitcoin should be required ($bitcoin given)")
  require(!fiat.isNegative, s"Cannot require a negative amount of fiat ($fiat given)")
}
