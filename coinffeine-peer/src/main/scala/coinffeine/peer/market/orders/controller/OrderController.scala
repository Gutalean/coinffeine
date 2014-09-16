package coinffeine.peer.market.orders.controller

import org.slf4j.LoggerFactory

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.market.orders.controller.OrderFunds.Listener
import coinffeine.protocol.messages.brokerage.OrderMatch

/** Runs and order deciding when to accept/reject order matches and notifying order changes.
  *
  * @constructor
  * @param amountsCalculator  Used to compute amounts of fiat/bitcoin involved
  * @param network            Which network the order is running on
  * @param initialOrder       Order to run
  */
private[orders] class OrderController[C <: FiatCurrency](amountsCalculator: AmountsCalculator,
                                                         network: Network,
                                                         initialOrder: Order[C],
                                                         publisher: OrderPublication[C],
                                                         funds: OrderFunds) {
  private class OrderControllerContext(
      override val calculator: AmountsCalculator,
      override val network: Network,
      override val funds: OrderFunds,
      var _order: Order[C]) extends StateContext[C] {

    var state: State[C] = _

    override def order = _order
    override def isInMarket = publisher.isInMarket

    override def transitionTo(newState: State[C]): Unit = {
      state = newState
      newState.enter(context)
    }

    override def startExchange(newExchange: NonStartedExchange[C]): Unit = {
      updateExchange(newExchange)
      // TODO: actually start an exchange actor
    }

    override def keepInMarket(): Unit = {
      publisher.keepPublishing()
    }

    override def keepOffMarket(): Unit = {
      publisher.stopPublishing()
    }

    override def updateOrderStatus(newStatus: OrderStatus): Unit = {
      val prevStatus = _order.status
      _order = _order.withStatus(newStatus)
      listeners.foreach(_.onStatusChanged(prevStatus, newStatus))
      if (newStatus.isFinal) {
        listeners.foreach(_.onFinish(newStatus))
      }
    }

    def updateExchange(exchange: AnyStateExchange[C]): Unit = {
      val prevProgress = _order.progress
      _order = _order.withExchange(exchange)
      val newProgress = _order.progress
      listeners.foreach(_.onProgress(prevProgress, newProgress))
    }

    def completeExchange(exchange: CompletedExchange[C]): Unit = {
      updateExchange(exchange)
      context.state.exchangeCompleted(context, exchange)
    }
  }

  private var listeners = Seq.empty[OrderController.Listener[C]]
  private val context = new OrderControllerContext(amountsCalculator, network, funds, initialOrder)
  publisher.addListener(new OrderPublication.Listener {
    override def inMarket(): Unit = {
      context.state.becomeInMarket(context)
    }
    override def offline(): Unit = {
      context.state.becomeOffline(context)
    }
  })
  funds.addListener(new Listener {
    override def onFundsUnavailable(funds: OrderFunds): Unit = {
      context.state.fundsBecomeUnavailable(context)
    }
    override def onFundsAvailable(funds: OrderFunds): Unit = {
      context.state.fundsBecomeAvailable(context)
    }
  })
  context.transitionTo(new StalledState)

  /** Immutable snapshot of the order */
  def view: Order[C] = context._order

  def addListener(listener: OrderController.Listener[C]): Unit = {
    listeners :+= listener
  }

  def acceptOrderMatch(orderMatch: OrderMatch): MatchResult[C] =
    context.state.acceptOrderMatch(context, orderMatch)
  def cancel(reason: String): Unit = { context.state.cancel(context, reason) }
  def updateExchange(exchange: AnyStateExchange[C]): Unit = { context.updateExchange(exchange) }
  def completeExchange(exchange: CompletedExchange[C]): Unit = { context.completeExchange(exchange) }
}

private[orders] object OrderController {
  private val Log = LoggerFactory.getLogger(classOf[OrderController[_]])

  trait Listener[C <: FiatCurrency] {
    def onProgress(oldProgress: Double, newProgress: Double): Unit
    def onStatusChanged(oldStatus: OrderStatus, newStatus: OrderStatus): Unit
    def onFinish(finalStatus: OrderStatus): Unit
  }
}
