package coinffeine.peer.payment

import coinffeine.common.properties.Property
import coinffeine.model.currency.balance.FiatBalances
import coinffeine.model.util.Cached

trait PaymentProcessorProperties {
  val balances: Property[Cached[FiatBalances]]
}
