package coinffeine.peer.payment.okpay.blocking

import coinffeine.model.currency.{FiatAmount, FiatCurrency}

/** Keeps a balance on several currencies */
private class MultipleBalance {

  private var byCurrency: Map[FiatCurrency, FiatAmount] = Map.empty

  def resetTo(newBalances: Seq[FiatAmount]): Unit = {
    byCurrency = newBalances.map(b => b.currency -> b).toMap
  }

  def incrementBalance(amount: FiatAmount): Unit = {
    val prevAmount = balanceFor(amount.currency)
    val newBalance = prevAmount + amount
    byCurrency = byCurrency.updated(amount.currency, newBalance)
  }

  def reduceBalance(amount: FiatAmount): Unit = {
    val prevAmount = balanceFor(amount.currency)
    val zero = amount.currency.zero
    val newBalance = (prevAmount - amount).max(zero)
    byCurrency = byCurrency.updated(amount.currency, newBalance)
  }

  def balanceFor(currency: FiatCurrency): FiatAmount = {
    val zero = currency.zero
    byCurrency.getOrElse(currency, zero).asInstanceOf[FiatAmount]
  }
}
