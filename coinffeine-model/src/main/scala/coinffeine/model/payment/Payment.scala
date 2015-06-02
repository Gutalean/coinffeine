package coinffeine.model.payment

import org.joda.time.DateTime

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}

case class Payment[C <: FiatCurrency](
  id: String,
  senderId: String,
  receiverId: String,
  amount: CurrencyAmount[C],
  date: DateTime,
  description: String,
  invoice: String,
  completed: Boolean
)
