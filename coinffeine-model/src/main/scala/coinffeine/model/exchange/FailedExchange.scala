package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.exchange.Exchange.Progress

case class FailedExchange(
    prev: ActiveExchange,
    timestamp: DateTime,
    cause: FailureCause,
    user: Option[Exchange.PeerInfo] = None,
    transaction: Option[ImmutableTransaction] = None) extends CompletedExchange {

  override val status = ExchangeStatus.Failed(cause)
  override val metadata = prev.metadata
  override val progress: Progress = prev.progress
  override val isSuccess = false

  override lazy val log = prev.log.record(status, timestamp)
}
