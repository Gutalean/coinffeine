package coinffeine.peer.market.orders.controller

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.NonStartedExchange
import coinffeine.model.market.{Order, OrderStatus}
import coinffeine.peer.amounts.AmountsCalculator

private[controller] trait StateContext[C <: FiatCurrency] {
  def order: Order[C]
  def funds: OrderFunds
  def calculator: AmountsCalculator
  def network: Network
  def isInMarket: Boolean

  def updateOrderStatus(newStatus: OrderStatus): Unit
  def transitionTo(state: State[C]): Unit
  def startExchange(newExchange: NonStartedExchange[C]): Unit
  def keepInMarket(): Unit
  def keepOffMarket(): Unit
}
