package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency

case class AbortingExchange[C <: FiatCurrency](prev: Exchange[C],
                                               timestamp: DateTime,
                                               cause: AbortionCause,
                                               user: Exchange.PeerInfo,
                                               refundTx: ImmutableTransaction) extends Exchange[C] {

  override val status = ExchangeStatus.Aborting(cause)
  override val metadata = prev.metadata
  override val isCompleted = false
  override val isStarted = true
  override val progress = Exchange.noProgress(currency)

  override lazy val log = prev.log.record(status, timestamp)

  def broadcast(transaction: ImmutableTransaction, timestamp: DateTime): FailedExchange[C] =
    FailedExchange(this, timestamp, FailureCause.Abortion(cause), Some(user), Some(transaction))

  def failedToBroadcast(timestamp: DateTime): FailedExchange[C] =
    FailedExchange(this, timestamp, FailureCause.Abortion(cause), Some(user), None)
}
