package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.market.RequiredFunds

sealed trait MatchResult[C <: FiatCurrency]

case class MatchAccepted[C <: FiatCurrency](funds: RequiredFunds[C]) extends MatchResult[C]

case class MatchRejected[C <: FiatCurrency](reason: String) extends MatchResult[C]

case class MatchAlreadyAccepted[C <: FiatCurrency](exchange: Exchange[C])
  extends MatchResult[C]
