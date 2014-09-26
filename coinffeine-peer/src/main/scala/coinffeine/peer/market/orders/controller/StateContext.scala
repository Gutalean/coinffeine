package coinffeine.peer.market.orders.controller

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderStatus, RequiredFunds}
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] trait StateContext[C <: FiatCurrency] {
  /** Order being controlled */
  def order: Order[C]
  def calculator: AmountsCalculator
  def network: Network
  /** Whether the order is published or not */
  def isInMarket: Boolean

  /** Allow to transition to the next state */
  def transitionTo(state: State[C]): Unit

  /** Modify order status */
  def updateOrderStatus(newStatus: OrderStatus): Unit

  /** Accept or reject a previously received order match */
  def resolveOrderMatch(orderMatch: OrderMatch[C], result: MatchResult[C]): Unit

  /** Request to block funds for exclusive use */
  def blockFunds(funds: RequiredFunds[C]): Unit

  /** Start publishing the order on the corresponding market */
  def keepInMarket(): Unit

  /** Stop publishing the order on the corresponding market */
  def keepOffMarket(): Unit
}
