package coinffeine.model.exchange

import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.Progress
import coinffeine.model.network.PeerId
import coinffeine.model.{ActivityLog, Both}

/** Historical view of an exchange */
case class ArchivedExchange(
    override val id: ExchangeId,
    override val role: Role,
    override val exchangedBitcoin: Both[BitcoinAmount],
    override val exchangedFiat: Both[FiatAmount],
    override val counterpartId: PeerId,
    override val lockTime: Long,
    override val log: ActivityLog[ExchangeStatus],
    override val progress: Progress) extends Exchange {
  require(log.activities.nonEmpty, "Log of activities should not be empty")
  override def status = log.mostRecent.get.event
  override val isCompleted = true
  override val isStarted = true
}
