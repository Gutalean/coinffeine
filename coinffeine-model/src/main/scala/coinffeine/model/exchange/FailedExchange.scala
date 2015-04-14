package coinffeine.model.exchange

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange.Progress

case class FailedExchange[C <: FiatCurrency](
    prev: Exchange[C],
    cause: FailureCause,
    user: Option[Exchange.PeerInfo] = None,
    transaction: Option[ImmutableTransaction] = None) extends CompletedExchange[C] {

  override val status = ExchangeStatus.Failed(cause)
  override val metadata = prev.metadata
  override val progress: Progress = prev.progress
  override val isSuccess = false
}
