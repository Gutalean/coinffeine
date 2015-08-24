package coinffeine.peer.properties.fiat

import akka.actor.ActorSystem

import coinffeine.common.akka.event.EventObservedProperty
import coinffeine.model.currency.balance.FiatBalances
import coinffeine.model.util.Cached
import coinffeine.peer.events.fiat.FiatBalanceChanged
import coinffeine.peer.payment.PaymentProcessorProperties

class DefaultPaymentProcessorProperties(implicit system: ActorSystem)
    extends PaymentProcessorProperties {

  override val balances = EventObservedProperty[Cached[FiatBalances]](
    FiatBalanceChanged.Topic, Cached.fresh(FiatBalances.empty)) {
    case FiatBalanceChanged(newBalances) => newBalances
  }
}
