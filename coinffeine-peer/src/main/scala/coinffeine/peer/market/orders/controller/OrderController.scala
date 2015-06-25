package coinffeine.peer.market.orders.controller

import org.joda.time.DateTime

import coinffeine.model.bitcoin.Network
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.order.ActiveOrder
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
private[orders] class OrderController(
    peerId: PeerId,
    amountsCalculator: AmountsCalculator,
    network: Network,
    initialOrder: ActiveOrder) {

  import OrderController._

  private val orderMatchValidator = new OrderMatchValidator(peerId, amountsCalculator)
  private var listeners = Seq.empty[OrderController.Listener]
  private var _order = initialOrder
  private var _pendingFundRequests = Map.empty[ExchangeId, FundsRequest]

  /** Immutable snapshot of the order */
  def view: ActiveOrder = _order

  def reset(order: ActiveOrder, pendingFundsRequests: Map[ExchangeId, FundsRequest]): Unit = {
    _order = order
    _pendingFundRequests = pendingFundsRequests
  }

  def updateExchange(exchange: ActiveExchange): Unit = {
    updateOrder(_.withExchange(exchange))
  }

  def completeExchange(exchange: CompletedExchange): Unit = {
    updateExchange(exchange)
  }

  def addListener(listener: OrderController.Listener): Unit = {
    listeners :+= listener
    listener.onOrderChange(_order, _order)
  }

  def shouldAcceptOrderMatch(orderMatch: OrderMatch): MatchResult =
    orderMatchValidator.shouldAcceptOrderMatch(_order, orderMatch, totalPendingFundsAmount)

  private def totalPendingFundsAmount: BitcoinAmount = {
    val role = Role.fromOrderType(_order.orderType)
    _pendingFundRequests.values.map(request => role.select(request.orderMatch.bitcoinAmount)).sum
  }

  def pendingFundRequests: Map[ExchangeId, FundsRequest] = _pendingFundRequests

  def pendingFunds: Map[ExchangeId, RequiredFunds] = _pendingFundRequests.mapValues(_.funds)

  def hasPendingFunds(exchangeId: ExchangeId): Boolean = _pendingFundRequests.contains(exchangeId)

  /** Mark exchange required funds as being requested */
  def fundsRequested(orderMatch: OrderMatch, requiredFunds: RequiredFunds): Unit = {
    _pendingFundRequests += orderMatch.exchangeId -> FundsRequest(orderMatch, requiredFunds)
  }

  /** Resolve as failed the funds for an exchange */
  def fundsRequestFailed(exchangeId: ExchangeId): Unit = {
    _pendingFundRequests -= exchangeId
  }

  /** Start an exchange. You should have called [[fundsRequested()]] previously. */
  def startExchange(exchangeId: ExchangeId, timestamp: DateTime): HandshakingExchange = {
    val request = _pendingFundRequests.getOrElse(exchangeId,
      throw new IllegalArgumentException(s"Cannot accept $exchangeId: no funds were blocked"))
    _pendingFundRequests -= exchangeId
    val newExchange = ActiveExchange.create(
      id = request.orderMatch.exchangeId,
      role = Role.fromOrderType(view.orderType),
      counterpartId = request.orderMatch.counterpart,
      amounts = amountsCalculator.exchangeAmountsFor(request.orderMatch),
      parameters = ActiveExchange.Parameters(request.orderMatch.lockTime, network),
      createdOn = timestamp
    )
    updateExchange(newExchange)
    newExchange
  }

  def becomeInMarket(): Unit = { updateOrder(_.becomeInMarket) }
  def becomeOffline(): Unit = { updateOrder(_.becomeOffline) }
  def cancel(timestamp: DateTime): Unit = {
    // TODO: is this what we wanna do if an exchange is running?
    updateOrder(_.cancel(timestamp))
  }

  private def updateOrder(mutator: ActiveOrder => ActiveOrder): Unit = {
    val previousOrder = _order
    val newOrder = mutator(_order)
    if (previousOrder != newOrder) {
      _order = newOrder
      listeners.foreach(_.onOrderChange(previousOrder, _order))
    }
  }
}

private[orders] object OrderController {
  trait Listener {
    def onOrderChange(oldOrder: ActiveOrder, newOrder: ActiveOrder): Unit
  }

  case class FundsRequest(orderMatch: OrderMatch, funds: RequiredFunds)
}
