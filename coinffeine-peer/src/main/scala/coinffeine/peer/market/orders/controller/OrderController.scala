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

  private var listeners = Seq.empty[OrderController.Listener[C]]
  private var _order = initialOrder

  /** Immutable snapshot of the order */
  def view: Order[C] = _order

  def updateExchange(exchange: Exchange[C]): Unit = {
    updateOrder(_.withExchange(exchange))
  }

  def completeExchange(exchange: CompletedExchange[C]): Unit = {
    updateExchange(exchange)
  }

  def addListener(listener: OrderController.Listener[C]): Unit = {
    listeners :+= listener
    listener.onOrderChange(_order, _order)
  }

  def shouldAcceptOrderMatch(orderMatch: OrderMatch[C]): MatchResult[C] =
    new OrderMatchValidator(amountsCalculator).shouldAcceptOrderMatch(_order, orderMatch)

  def acceptOrderMatch(orderMatch: OrderMatch[C]): HandshakingExchange[C] = {
    val newExchange = Exchange.handshaking(
      id = orderMatch.exchangeId,
      Role.fromOrderType(view.orderType),
      counterpartId = orderMatch.counterpart,
      amountsCalculator.exchangeAmountsFor(orderMatch),
      Exchange.Parameters(orderMatch.lockTime, network)
    )
    updateExchange(newExchange)
    newExchange
  }

  def start(): Unit = { updateOrder(_.start) }
  def becomeInMarket(): Unit = { updateOrder(_.becomeInMarket) }
  def becomeOffline(): Unit = { updateOrder(_.becomeOffline) }
  def cancel(): Unit = {
    // TODO: is this what we wanna do if an exchange is running?
    updateOrder(_.cancel)
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

private[orders] object OrderController {
  trait Listener[C <: FiatCurrency] {
    def onOrderChange(oldOrder: Order[C], newOrder: Order[C]): Unit
  }
}
