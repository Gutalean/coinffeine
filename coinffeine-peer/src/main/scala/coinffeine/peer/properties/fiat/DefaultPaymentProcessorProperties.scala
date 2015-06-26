package coinffeine.peer.properties.fiat

import akka.actor.ActorSystem

import coinffeine.common.akka.event.EventObservedPropertyMap
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.balance.FiatBalance
import coinffeine.peer.events.fiat.BalanceChanged
import coinffeine.peer.payment.PaymentProcessorProperties

class DefaultPaymentProcessorProperties(implicit system: ActorSystem)
  extends PaymentProcessorProperties {

  override val balance = EventObservedPropertyMap[FiatCurrency, FiatBalance](BalanceChanged.Topic) {
    case BalanceChanged(newBalance) =>
      EventObservedPropertyMap.Put(newBalance.amount.currency, newBalance)
  }
}
