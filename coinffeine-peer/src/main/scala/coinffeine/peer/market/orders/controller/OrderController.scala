package coinffeine.peer.market.orders.controller

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.market.orders.controller.FundsBlocker.Listener
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
    initialOrder: Order[C],
    publisher: OrderPublication[C],
    fundsBlocker: FundsBlocker) {

  private class OrderControllerContext(
      override val calculator: AmountsCalculator,
      override val network: Network,
      var _order: Order[C]) extends StateContext[C] {

    var state: State[C] = _

    override def order = _order
    override def isInMarket = publisher.isInMarket

    override def transitionTo(newState: State[C]): Unit = {
      state = newState
      newState.enter(context)
    }

    override def resolveOrderMatch(orderMatch: OrderMatch[C], result: MatchResult[C]): Unit = {
      result match {
        case MatchAccepted(exchange) => updateExchange(exchange)
        case _ =>
      }
      listeners.foreach(_.onOrderMatchResolution(orderMatch, result))
    }

    override def blockFunds(id: ExchangeId, funds: RequiredFunds[C]): Unit =
      fundsBlocker.blockFunds(id, funds, new Listener {
        override def onSuccess(): Unit = {
          state.fundsBlocked(context)
        }
        override def onFailure(cause: Throwable): Unit = {
          state.cannotBlockFunds(context, cause)
        }
      })

    override def keepInMarket(): Unit = {
      publisher.keepPublishing(order.amounts.pending)
    }

    override def keepOffMarket(): Unit = {
      publisher.stopPublishing()
    }

    override def updateOrderStatus(newStatus: OrderStatus): Unit = {
      updateOrder(_.withStatus(newStatus))
    }

    def updateExchange(exchange: AnyStateExchange[C]): Unit = {
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
  private val context = new OrderControllerContext(amountsCalculator, network, initialOrder)
  publisher.addListener(new OrderPublication.Listener {
    override def inMarket(): Unit = {
      context.state.becomeInMarket(context)
    }
    override def offline(): Unit = {
      context.state.becomeOffline(context)
    }
  })
  context.transitionTo(new WaitingForMatchesState[C])

  /** Immutable snapshot of the order */
  def view: Order[C] = context._order

  def addListener(listener: OrderController.Listener[C]): Unit = {
    listeners :+= listener
    listener.onOrderChange(context._order, context._order)
  }

  def acceptOrderMatch(orderMatch: OrderMatch[C]): Unit = {
    context.state.acceptOrderMatch(context, orderMatch)
  }
  def cancel(reason: String): Unit = { context.state.cancel(context, reason) }
  def updateExchange(exchange: AnyStateExchange[C]): Unit = { context.updateExchange(exchange) }
  def completeExchange(exchange: CompletedExchange[C]): Unit = { context.completeExchange(exchange) }
}

private[orders] object OrderController {
  trait Listener[C <: FiatCurrency] {
    def onOrderChange(oldOrder: Order[C], newOrder: Order[C]): Unit
    def onOrderMatchResolution(orderMatch: OrderMatch[C], result: MatchResult[C]): Unit
  }
}
