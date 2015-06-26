package coinffeine.peer.properties.fiat

import akka.actor.ActorSystem

import coinffeine.common.akka.event.{EventObservedProperty, EventObservedPropertyMap}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.balance.{FiatBalances, CachedFiatBalances, FiatBalance}
import coinffeine.peer.events.fiat.BalanceChanged
import coinffeine.peer.payment.PaymentProcessorProperties

class DefaultPaymentProcessorProperties(implicit system: ActorSystem)
    extends PaymentProcessorProperties {

  override val balances =
    EventObservedProperty[CachedFiatBalances](
      BalanceChanged.Topic, CachedFiatBalances.fresh(FiatBalances.empty)) {
      case BalanceChanged(newBalances) => newBalances
    }
}
