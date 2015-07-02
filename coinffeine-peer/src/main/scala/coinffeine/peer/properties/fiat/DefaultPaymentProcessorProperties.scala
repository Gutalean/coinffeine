package coinffeine.peer.properties.fiat

import akka.actor.ActorSystem

import coinffeine.common.akka.event.EventObservedProperty
import coinffeine.model.currency.FiatAmounts
import coinffeine.model.util.Cached
import coinffeine.peer.events.fiat.{RemainingLimitsChanged, BalanceChanged}
import coinffeine.peer.payment.PaymentProcessorProperties

class DefaultPaymentProcessorProperties(implicit system: ActorSystem)
    extends PaymentProcessorProperties {

  override val balances =
    EventObservedProperty[Cached[FiatAmounts]](
      BalanceChanged.Topic, Cached.fresh(FiatAmounts.empty)) {
      case BalanceChanged(newBalances) => newBalances
    }

  override val remainingLimits =
    EventObservedProperty[Cached[FiatAmounts]](
      RemainingLimitsChanged.Topic, Cached.fresh(FiatAmounts.empty)) {
      case RemainingLimitsChanged(newBalances) => newBalances
    }
}
