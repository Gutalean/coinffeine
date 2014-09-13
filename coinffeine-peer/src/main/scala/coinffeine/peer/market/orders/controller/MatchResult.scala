package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._

sealed trait MatchResult[C <: FiatCurrency]

case class MatchAccepted[C <: FiatCurrency](exchange: NonStartedExchange[C])
  extends MatchResult[C]

case class MatchRejected[C <: FiatCurrency](reason: String) extends MatchResult[C]

case class MatchAlreadyAccepted[C <: FiatCurrency](exchange: AnyStateExchange[C])
  extends MatchResult[C]
