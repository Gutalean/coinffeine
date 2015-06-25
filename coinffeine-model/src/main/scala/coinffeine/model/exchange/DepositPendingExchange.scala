package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.bitcoin.ImmutableTransaction

case class DepositPendingExchange(
    prev: HandshakingExchange,
    timestamp: DateTime,
    user: Exchange.PeerInfo,
    counterpart: Exchange.PeerInfo) extends AfterHandshakeExchange {

  override val status = ExchangeStatus.WaitingDepositConfirmation(user, counterpart)
  override val metadata = prev.metadata
  override val progress = Exchange.noProgress
  override val isCompleted = false

  override lazy val log = prev.log.record(status, timestamp)

  def startExchanging(deposits: ActiveExchange.Deposits, timestamp: DateTime): RunningExchange =
    RunningExchange(this, deposits, timestamp)

  def cancel(cause: CancellationCause, timestamp: DateTime): FailedExchange =
    FailedExchange(this, timestamp, FailureCause.Cancellation(cause), Some(user))

  def abort(cause: AbortionCause,
            refundTx: ImmutableTransaction,
            timestamp: DateTime): AbortingExchange =
    AbortingExchange(this, timestamp, cause, user, refundTx)
}
