package coinffeine.model.order

import coinffeine.model.ActivityLog
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.exchange.{ArchivedExchange, ExchangeId}

/** An historical view on an order */
case class ArchivedOrder(
    override val id: OrderId,
    override val orderType: OrderType,
    override val amount: BitcoinAmount,
    override val price: OrderPrice,
    override val exchanges: Map[ExchangeId, ArchivedExchange],
    override val log: ActivityLog[OrderStatus]) extends Order {
  require(log.activities.nonEmpty, "Log of activities should not be empty")
  override val inMarket = false
  override val cancellable = false
  override def status = log.mostRecent.get.event
}
