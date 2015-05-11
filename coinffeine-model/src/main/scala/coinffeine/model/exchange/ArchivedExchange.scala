package coinffeine.model.exchange

import coinffeine.model.ActivityLog
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange.Progress

/** Historical view of an exchange */
case class ArchivedExchange[C <: FiatCurrency](
    override val metadata: ExchangeMetadata[C],
    override val log: ActivityLog[ExchangeStatus],
    override val progress: Progress) extends Exchange[C] {
  require(log.activities.nonEmpty, "Log of activities should not be empty")
  override def status = log.mostRecent.get.event
  override val isCompleted = true
  override val isStarted = true
}
