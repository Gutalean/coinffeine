package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.ActivityLog
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency

case class HandshakingExchange[C <: FiatCurrency](metadata: ExchangeMetadata[C]) extends Exchange[C] {

  override val status = ExchangeStatus.Handshaking
  override val progress = Exchange.noProgress(currency)
  override val isCompleted = false
  override val isStarted = false

  override lazy val log = ActivityLog(ExchangeStatus.Handshaking, metadata.createdOn)

  def handshake(user: Exchange.PeerInfo,
                counterpart: Exchange.PeerInfo,
                timestamp: DateTime): DepositPendingExchange[C] =
    DepositPendingExchange(this, timestamp, user, counterpart)

  def cancel(cause: CancellationCause,
             user: Option[Exchange.PeerInfo] = None,
             timestamp: DateTime): FailedExchange[C] =
    FailedExchange(this, timestamp, FailureCause.Cancellation(cause), user)

  def abort(cause: AbortionCause,
            user: Exchange.PeerInfo,
            refundTx: ImmutableTransaction,
            timestamp: DateTime): AbortingExchange[C] =
    AbortingExchange(this, timestamp, cause, user, refundTx)

  def withId(id: ExchangeId) = copy(metadata = metadata.copy(id = id))
}
