package coinffeine.peer.payment.okpay.blocking

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}

/** Keeps a balance on several currencies */
private class MultipleBalance {

  private var byCurrency: Map[FiatCurrency, FiatAmount] = Map.empty

  def resetTo(newBalances: Seq[FiatAmount]): Unit = {
    byCurrency = newBalances.map(b => b.currency -> b).toMap
  }

  def incrementBalance[C <: FiatCurrency](amount: CurrencyAmount[C]): Unit = {
    val prevAmount = balanceFor(amount.currency)
    val newBalance = prevAmount + amount
    byCurrency = byCurrency.updated(amount.currency, newBalance)
  }

  def reduceBalance[C <: FiatCurrency](amount: CurrencyAmount[C]): Unit = {
    val prevAmount = balanceFor(amount.currency)
    val zero = CurrencyAmount.zero(amount.currency)
    val newBalance = (prevAmount - amount).max(zero)
    byCurrency = byCurrency.updated(amount.currency, newBalance)
  }

  def balanceFor[C <: FiatCurrency](currency: C): CurrencyAmount[C] = {
    val zero = CurrencyAmount.zero(currency)
    byCurrency.getOrElse(currency, zero).asInstanceOf[CurrencyAmount[C]]
  }
}
