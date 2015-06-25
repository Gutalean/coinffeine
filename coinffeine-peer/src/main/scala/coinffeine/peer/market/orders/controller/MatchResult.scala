package coinffeine.peer.market.orders.controller

import coinffeine.model.exchange._
import coinffeine.model.market.RequiredFunds

sealed trait MatchResult

case class MatchAccepted(funds: RequiredFunds) extends MatchResult

case class MatchRejected(reason: String) extends MatchResult

case class MatchAlreadyAccepted(exchange: Exchange) extends MatchResult
