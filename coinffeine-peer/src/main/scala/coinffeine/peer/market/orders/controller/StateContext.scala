package coinffeine.peer.market.orders.controller

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.{Order, OrderStatus, RequiredFunds}
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.protocol.messages.brokerage.OrderMatch

private[controller] trait StateContext[C <: FiatCurrency] {
  /** Order being controlled */
  def order: Order[C]
  def calculator: AmountsCalculator
  def network: Network

  /** Allow to transition to the next state */
  def transitionTo(state: State[C]): Unit

  /** Modify order status */
  def updateOrderStatus(newStatus: OrderStatus): Unit

  /** Start publishing the order on the corresponding market */
  def keepInMarket(): Unit

  /** Stop publishing the order on the corresponding market */
  def keepOffMarket(): Unit
}
