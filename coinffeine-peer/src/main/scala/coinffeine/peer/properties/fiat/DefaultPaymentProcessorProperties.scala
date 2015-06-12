package coinffeine.peer.properties.fiat

import akka.actor.ActorSystem

import coinffeine.common.akka.event.EventObservedPropertyMap
import coinffeine.model.currency.{AnyFiatBalance, FiatCurrency}
import coinffeine.peer.events.fiat.BalanceChanged
import coinffeine.peer.payment.PaymentProcessorProperties

class DefaultPaymentProcessorProperties(implicit system: ActorSystem)
  extends PaymentProcessorProperties {

  override val balance = EventObservedPropertyMap[FiatCurrency, AnyFiatBalance](BalanceChanged.Topic) {
    case BalanceChanged(newBalance) =>
      EventObservedPropertyMap.Put(newBalance.amount.currency, newBalance)
  }
}
