package coinffeine.peer.api.event

import coinffeine.model.currency.{CurrencyAmount, Currency}

/** Represents a balance and whether it is a current or expired.
  *
  * @param amount      Amount of money in the balance
  * @param hasExpired  Whether the balance is not current information
  * @tparam C          Currency of the balance
  */
case class Balance[C <: Currency](amount: CurrencyAmount[C], hasExpired: Boolean = false)
