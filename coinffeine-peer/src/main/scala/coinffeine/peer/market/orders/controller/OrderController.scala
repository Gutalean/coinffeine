package coinffeine.peer.market.orders.controller

import scala.util.Try

import org.slf4j.LoggerFactory

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange.BlockedFunds
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
private[orders] class OrderController[C <: FiatCurrency](amountsCalculator: AmountsCalculator,
                                                         network: Network,
                                                         val initialOrder: Order[C],
                                                         publisher: OrderPublication[C]) {

  import OrderController._

  val id = initialOrder.id

  private class OrderControllerContext(
      override val calculator: AmountsCalculator,
      override val network: Network,
      var _order: Order[C],
      var _funds: OrderFunds = NoFunds) extends StateContext[C] {

    var state: State[C] = _

    override def order = _order
    override def funds = _funds
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

    def completeExchange(exchange: Try[CompletedExchange[C]]): Unit = {
      exchange.toOption.foreach(updateExchange)
      context.state.exchangeCompleted(context, exchange)
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
  context.transitionTo(new StalledState)

  /** Immutable snapshot of the order */
  def view: Order[C] = context._order

  def addListener(listener: OrderController.Listener[C]): Unit = {
    listeners :+= listener
  }

  def requiredFunds: (BitcoinAmount, CurrencyAmount[C]) = {
    val amounts = amountsCalculator.exchangeAmountsFor(context._order)
    val role = Role.fromOrderType(initialOrder.orderType)
    (role.select(amounts.bitcoinRequired), role.select(amounts.fiatRequired))
  }

  def fundsBecomeAvailable(newFunds: BlockedFunds): Unit = {
    context._funds match {
      case AvailableFunds(oldFunds) =>
        Log.warn("Notified of {} became available while {} ware already available",
          Seq(newFunds, oldFunds): _*)
      case UnavailableFunds(oldFunds) if newFunds != oldFunds =>
        throw new IllegalArgumentException(
          s"$newFunds become available while waiting for $oldFunds to become so")
      case _ =>
        context._funds = AvailableFunds(newFunds)
        context.state.fundsBecomeAvailable(context)
    }
  }

  def fundsBecomeUnavailable(): Unit = {
    context._funds match {
      case NoFunds =>
        Log.warn("Notified of funds became unavailable before getting them blocked")
      case UnavailableFunds(fundsId) =>
        Log.warn("Notified of {} became unavailable when they were already so", fundsId)
      case AvailableFunds(fundsId) =>
        context._funds = UnavailableFunds(fundsId)
        context.state.fundsBecomeUnavailable(context)
    }
  }

  def acceptOrderMatch(orderMatch: OrderMatch): MatchResult[C] =
    context.state.acceptOrderMatch(context, orderMatch)
  def cancel(reason: String): Unit = { context.state.cancel(context, reason) }
  def updateExchange(exchange: AnyStateExchange[C]): Unit = { context.updateExchange(exchange) }
  def completeExchange(exchange: Try[CompletedExchange[C]]): Unit = {
    context.completeExchange(exchange)
  }
}

private[orders] object OrderController {
  private val Log = LoggerFactory.getLogger(classOf[OrderController[_]])

  trait Listener[C <: FiatCurrency] {
    def onProgress(oldProgress: Double, newProgress: Double): Unit
    def onStatusChanged(oldStatus: OrderStatus, newStatus: OrderStatus): Unit
    def onFinish(finalStatus: OrderStatus): Unit
  }
}
