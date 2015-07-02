package coinffeine.peer.events.fiat

import coinffeine.common.akka.event.TopicProvider
import coinffeine.model.currency.FiatAmounts
import coinffeine.model.util.Cached

/** An event published when transference limits change. */
case class RemainingLimitsChanged(limits: Cached[FiatAmounts])

object RemainingLimitsChanged extends TopicProvider[RemainingLimitsChanged] {
  override val Topic = "fiat.remaining-limits-changed"
}
