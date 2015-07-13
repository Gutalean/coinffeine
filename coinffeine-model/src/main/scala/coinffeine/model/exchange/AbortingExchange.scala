package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.bitcoin.ImmutableTransaction

case class AbortingExchange(
  prev: ActiveExchange,
  timestamp: DateTime,
  cause: AbortionCause,
  user: Exchange.PeerInfo,
  refundTx: ImmutableTransaction) extends ActiveExchange {

  override val status = ExchangeStatus.Aborting(cause)
  override val metadata = prev.metadata
  override val isCompleted = false
  override val isStarted = true
  override val progress = Exchange.noProgress

  override lazy val log = prev.log.record(status, timestamp)

  def broadcast(transaction: ImmutableTransaction, timestamp: DateTime): FailedExchange =
    FailedExchange(this, timestamp, FailureCause.Abortion(cause), Some(user), Some(transaction))

  def failedToBroadcast(timestamp: DateTime): FailedExchange =
    FailedExchange(this, timestamp, FailureCause.Abortion(cause), Some(user), None)
}
