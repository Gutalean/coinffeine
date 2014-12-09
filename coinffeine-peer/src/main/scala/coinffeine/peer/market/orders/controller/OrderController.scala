package coinffeine.peer.market.orders.controller

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.protocol.messages.brokerage.OrderMatch

/** Runs and order deciding when to accept/reject order matches and notifying order changes.
  *
  * @constructor
  * @param amountsCalculator  Used to compute amounts of fiat/bitcoin involved
  * @param network            Which network the order is running on
  * @param initialOrder       Order to run
  */
private[orders] class OrderController[C <: FiatCurrency](
    amountsCalculator: AmountsCalculator,
    network: Network,
    initialOrder: Order[C]) {

  private class OrderControllerContext(
      override val calculator: AmountsCalculator,
      var _order: Order[C],
      var shouldBeOnMarket: Boolean = true) extends StateContext[C] {

    var state: State[C] = _

    override def order = _order

    override def transitionTo(newState: State[C]): Unit = {
      state = newState
      newState.enter(context)
    }

    override def keepInMarket(): Unit = {
      shouldBeOnMarket = true
      listeners.foreach(_.keepInMarket())
    }
    override def keepOffMarket(): Unit = {
      shouldBeOnMarket = false
      listeners.foreach(_.keepOffMarket())
    }

    override def updateOrderStatus(newStatus: OrderStatus): Unit = {
      updateOrder(_.withStatus(newStatus))
    }

    def updateExchange(exchange: Exchange[C]): Unit = {
      updateOrder(_.withExchange(exchange))
    }

    def completeExchange(exchange: CompletedExchange[C]): Unit = {
      updateExchange(exchange)
      context.state.exchangeCompleted(context, exchange)
    }

    private def updateOrder(mutator: Order[C] => Order[C]): Unit = {
      val previousOrder = _order
      val newOrder = mutator(_order)
      if (previousOrder != newOrder) {
        _order = newOrder
        listeners.foreach(_.onOrderChange(previousOrder, _order))
      }
    }
  }

  private var listeners = Seq.empty[OrderController.Listener[C]]
  private val context = new OrderControllerContext(amountsCalculator, initialOrder)
  context.transitionTo(new WaitingForMatchesState[C])

  /** Immutable snapshot of the order */
  def view: Order[C] = context._order

  def addListener(listener: OrderController.Listener[C]): Unit = {
    listeners :+= listener
    listener.onOrderChange(context._order, context._order)
    if (shouldBeOnMarket) listener.keepInMarket()
    else listener.keepOffMarket()
  }

  def shouldAcceptOrderMatch(orderMatch: OrderMatch[C]): MatchResult[C] =
    context.state.shouldAcceptOrderMatch(context, orderMatch)

  def acceptOrderMatch(orderMatch: OrderMatch[C]): NotStartedExchange[C] = {
    val newExchange = Exchange.notStarted(
      id = orderMatch.exchangeId,
      Role.fromOrderType(view.orderType),
      counterpartId = orderMatch.counterpart,
      amountsCalculator.exchangeAmountsFor(orderMatch),
      Exchange.Parameters(orderMatch.lockTime, network)
    )
    context.updateExchange(newExchange)
    context.state.acceptedOrderMatch(context, orderMatch)
    newExchange
  }

  def shouldBeOnMarket: Boolean = context.shouldBeOnMarket
  def becomeInMarket(): Unit = { context.updateOrderStatus(InMarketOrder) }
  def becomeOffline(): Unit = { context.updateOrderStatus(OfflineOrder) }
  def cancel(): Unit = { context.state.cancel(context) }
  def updateExchange(exchange: Exchange[C]): Unit = { context.updateExchange(exchange) }
  def completeExchange(exchange: CompletedExchange[C]): Unit = { context.completeExchange(exchange) }
}

private[orders] object OrderController {
  trait Listener[C <: FiatCurrency] {
    def onOrderChange(oldOrder: Order[C], newOrder: Order[C]): Unit
    def keepInMarket(): Unit
    def keepOffMarket(): Unit
  }
}
