package coinffeine.model.exchange

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange.Progress

case class SuccessfulExchange[C <: FiatCurrency](
    prev: RunningExchange[C]) extends AfterHandshakeExchange[C] with CompletedExchange[C] {

  override val status = ExchangeStatus.Successful
  override val metadata = prev.metadata
  override val progress = Progress(amounts.exchangedBitcoin)
  override val isSuccess = true
  override val user = prev.user
  override val counterpart = prev.counterpart
}
