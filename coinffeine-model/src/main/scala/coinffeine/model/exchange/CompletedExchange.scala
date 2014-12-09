package coinffeine.model.exchange

import coinffeine.model.currency.FiatCurrency

trait CompletedExchange[C <: FiatCurrency] extends Exchange[C] {

  override val isCompleted = true
  def isSuccess: Boolean
}
