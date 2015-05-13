package coinffeine.model.exchange

import coinffeine.model.currency.FiatCurrency

trait CompletedExchange[C <: FiatCurrency] extends ActiveExchange[C] {

  override val isCompleted = true
  override val isStarted = true
  def isSuccess: Boolean
}
