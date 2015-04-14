package coinffeine.peer.market.orders.controller

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.protocol.messages.brokerage.OrderMatch

/** Runs and order deciding when to accept/reject order matches and notifying order changes.
  *
  * @constructor
  * @param peerId             Own peer id
  * @param amountsCalculator  Used to compute amounts of fiat/bitcoin involved
  * @param network            Which network the order is running on
  * @param initialOrder       Order to run
  */
private[orders] class OrderController[C <: FiatCurrency](
    peerId: PeerId,
    amountsCalculator: AmountsCalculator,
    network: Network,
    initialOrder: Order[C]) {

  private case class FundsRequest(orderMatch: OrderMatch[C], funds: RequiredFunds[C])

  private val orderMatchValidator = new OrderMatchValidator(peerId, amountsCalculator)
  private var listeners = Seq.empty[OrderController.Listener[C]]
  private var _order = initialOrder
  private var pendingFundRequests = Map.empty[ExchangeId, FundsRequest]

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
    orderMatchValidator.shouldAcceptOrderMatch(_order, orderMatch, totalPendingFundsAmount)

  private def totalPendingFundsAmount: Bitcoin.Amount = {
    val role = Role.fromOrderType(_order.orderType)
    pendingFundRequests.values.map(request => role.select(request.orderMatch.bitcoinAmount)).sum
  }

  def pendingFunds: Map[ExchangeId, RequiredFunds[C]] = pendingFundRequests.mapValues(_.funds)

  def hasPendingFunds(exchangeId: ExchangeId): Boolean = pendingFundRequests.contains(exchangeId)

  /** Mark exchange required funds as being requested */
  def fundsRequested(orderMatch: OrderMatch[C], requiredFunds: RequiredFunds[C]): Unit = {
    pendingFundRequests += orderMatch.exchangeId -> FundsRequest(orderMatch, requiredFunds)
  }

  /** Resolve as failed the funds for an exchange */
  def fundsRequestFailed(exchangeId: ExchangeId): Unit = {
    pendingFundRequests -= exchangeId
  }

  /** Start an exchange. You should have called [[fundsRequested()]] previously. */
  def startExchange(exchangeId: ExchangeId): HandshakingExchange[C] = {
    val request = pendingFundRequests.getOrElse(exchangeId,
      throw new IllegalArgumentException(s"Cannot accept $exchangeId: no funds were blocked"))
    pendingFundRequests -= exchangeId
    val newExchange = Exchange.handshaking(
      id = request.orderMatch.exchangeId,
      Role.fromOrderType(view.orderType),
      counterpartId = request.orderMatch.counterpart,
      amountsCalculator.exchangeAmountsFor(request.orderMatch),
      Exchange.Parameters(request.orderMatch.lockTime, network)
    )
    updateExchange(newExchange)
    newExchange
  }

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
