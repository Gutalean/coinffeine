package coinffeine.model.order

import coinffeine.model.ActivityLog
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange.{ArchivedExchange, ExchangeId}

/** An historical view on an order */
case class ArchivedOrder[C <: FiatCurrency](
    override val id: OrderId,
    override val orderType: OrderType,
    override val amount: Bitcoin.Amount,
    override val price: OrderPrice[C],
    override val exchanges: Map[ExchangeId, ArchivedExchange[C]],
    override val log: ActivityLog[OrderStatus]) extends Order[C] {
  require(log.activities.nonEmpty, "Log of activities should not be empty")
  override val inMarket = false
  override def status = log.mostRecent.get.event
}
