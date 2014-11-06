package coinffeine.peer.market

import akka.actor.ActorRef

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.OrderBookEntry

package object submission {
  private[submission] type SubmittingOrders[C <: FiatCurrency] = Set[(ActorRef, OrderBookEntry[C])]
  private[submission] val SubmittingOrders = Set
}
