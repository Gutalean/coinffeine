package coinffeine.peer.market.orders.controller

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.Order
import coinffeine.peer.amounts.AmountsCalculator

private[controller] trait StateContext[C <: FiatCurrency] {
  /** Order being controlled */
  def order: Order[C]
  def calculator: AmountsCalculator

  /** Allow to transition to the next state */
  def transitionTo(state: State[C]): Unit

  /** Start publishing the order on the corresponding market */
  def keepInMarket(): Unit

  /** Stop publishing the order on the corresponding market */
  def keepOffMarket(): Unit
}
