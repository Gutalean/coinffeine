package coinffeine.peer.payment

import coinffeine.common.properties.Property
import coinffeine.model.currency.balance.CachedFiatBalances

trait PaymentProcessorProperties {
  val balances: Property[CachedFiatBalances]
}
