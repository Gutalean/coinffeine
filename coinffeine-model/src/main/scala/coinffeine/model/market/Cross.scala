package coinffeine.model.market

import coinffeine.model.currency._
import coinffeine.model.exchange.Both

/** Total or partial cross between a bidding and an asking position.
  *
  * @param bitcoinAmounts  Net and gross bitcoin amounts to exchange
  * @param fiatAmounts     Gross and net fiat amounts to exchange
  * @param positions       Bidding and asking positions
  * @tparam C              Currency exchanged by bitcoin
  */
case class Cross[C <: FiatCurrency](bitcoinAmounts: Both[BitcoinAmount],
                                    fiatAmounts: Both[CurrencyAmount[C]],
                                    positions: Both[PositionId])

