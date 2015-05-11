package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency

case class DepositPendingExchange[C <: FiatCurrency](
    prev: HandshakingExchange[C],
    timestamp: DateTime,
    user: Exchange.PeerInfo,
    counterpart: Exchange.PeerInfo) extends AfterHandshakeExchange[C] {

  override val status = ExchangeStatus.WaitingDepositConfirmation(user, counterpart)
  override val metadata = prev.metadata
  override val progress = Exchange.noProgress(currency)
  override val isCompleted = false

  override lazy val log = prev.log.record(status, timestamp)

  def startExchanging(deposits: Exchange.Deposits, timestamp: DateTime): RunningExchange[C] =
    RunningExchange(this, deposits, timestamp)

  def cancel(cause: CancellationCause, timestamp: DateTime): FailedExchange[C] =
    FailedExchange(this, timestamp, FailureCause.Cancellation(cause), Some(user))

  def abort(cause: AbortionCause,
            refundTx: ImmutableTransaction,
            timestamp: DateTime): AbortingExchange[C] =
    AbortingExchange(this, timestamp, cause, user, refundTx)
}
