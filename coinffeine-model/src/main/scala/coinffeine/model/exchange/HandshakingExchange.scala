package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.ActivityLog
import coinffeine.model.bitcoin.ImmutableTransaction

case class HandshakingExchange(metadata: ExchangeMetadata)
  extends ActiveExchange {

  override val status = ExchangeStatus.Handshaking
  override val progress = Exchange.noProgress
  override val isCompleted = false
  override val isStarted = false

  override lazy val log = ActivityLog(ExchangeStatus.Handshaking, metadata.createdOn)

  def handshake(user: Exchange.PeerInfo,
                counterpart: Exchange.PeerInfo,
                timestamp: DateTime): DepositPendingExchange =
    DepositPendingExchange(this, timestamp, user, counterpart)

  def cancel(cause: CancellationCause,
             user: Option[Exchange.PeerInfo] = None,
             timestamp: DateTime): FailedExchange =
    FailedExchange(this, timestamp, FailureCause.Cancellation(cause), user)

  def abort(cause: AbortionCause,
            user: Exchange.PeerInfo,
            refundTx: ImmutableTransaction,
            timestamp: DateTime): AbortingExchange =
    AbortingExchange(this, timestamp, cause, user, refundTx)

  def withId(id: ExchangeId) = copy(metadata = metadata.copy(id = id))
}
