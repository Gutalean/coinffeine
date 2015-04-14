package coinffeine.model.exchange

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency

case class AbortingExchange[C <: FiatCurrency](prev: Exchange[C],
                                               cause: AbortionCause,
                                               user: Exchange.PeerInfo,
                                               refundTx: ImmutableTransaction) extends Exchange[C] {

  override val status = ExchangeStatus.Aborting(cause)
  override val metadata = prev.metadata
  override val isCompleted = false
  override val isStarted = true
  override val progress = Exchange.noProgress(currency)

  def broadcast(transaction: ImmutableTransaction): FailedExchange[C] =
    FailedExchange(this, FailureCause.Abortion(cause), Some(user), Some(transaction))

  def failedToBroadcast: FailedExchange[C] =
    FailedExchange(this, FailureCause.Abortion(cause), Some(user), None)
}
