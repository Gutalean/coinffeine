package coinffeine.model.exchange

import coinffeine.model.network.PeerId
import coinffeine.model.{Both, ActivityLog}
import coinffeine.model.currency.{Bitcoin, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange.Progress

/** Historical view of an exchange */
case class ArchivedExchange[C <: FiatCurrency](
    override val id: ExchangeId,
    override val role: Role,
    override val exchangedBitcoin: Both[Bitcoin.Amount],
    override val exchangedFiat: Both[CurrencyAmount[C]],
    override val counterpartId: PeerId,
    override val lockTime: Long,
    override val log: ActivityLog[ExchangeStatus],
    override val progress: Progress) extends Exchange[C] {
  require(log.activities.nonEmpty, "Log of activities should not be empty")
  override def status = log.mostRecent.get.event
  override val isCompleted = true
  override val isStarted = true
}
