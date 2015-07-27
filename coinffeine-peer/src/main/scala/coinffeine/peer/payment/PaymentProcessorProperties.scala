package coinffeine.peer.payment

import coinffeine.common.properties.Property
import coinffeine.model.currency.FiatAmounts
import coinffeine.model.currency.balance.FiatBalance
import coinffeine.model.util.Cached

trait PaymentProcessorProperties {
  val balances: Property[Cached[FiatBalance]]
  val remainingLimits: Property[Cached[FiatAmounts]]
}
