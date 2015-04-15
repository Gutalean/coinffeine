package coinffeine.model.market

import scalaz.Scalaz._

import coinffeine.model.Both
import coinffeine.model.currency._
import coinffeine.model.exchange.Role

/** Total or partial cross between a bidding and an asking position.
  *
  * @param bitcoinAmounts  Net and gross bitcoin amounts to exchange
  * @param fiatAmounts     Gross and net fiat amounts to exchange
  * @param positions       Bidding and asking positions
  * @tparam C              Currency exchanged by bitcoin
  */
case class Cross[C <: FiatCurrency](bitcoinAmounts: Both[Bitcoin.Amount],
                                    fiatAmounts: Both[CurrencyAmount[C]],
                                    positions: Both[PositionId]) {

  /** Decrease the available amount of a position by the amount in this cross.
    * Return [[None]] for perfect matches
    */
  def decreasePosition[T <: OrderType](position: Position[T, C]): Option[Position[T, C]] = {
    val role = Role.fromOrderType(position.orderType)
    val crossedAmount = role.select(bitcoinAmounts)
    (position.availableAmount > crossedAmount)
      .option(position.startHandshake(crossedAmount))
  }
}

