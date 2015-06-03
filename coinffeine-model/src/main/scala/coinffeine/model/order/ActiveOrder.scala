package coinffeine.model.order

import org.joda.time.DateTime

import coinffeine.model.ActivityLog
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market.OrderBookEntry

/** An order represents a process initiated by a peer to bid (buy) or ask(sell) bitcoins in
  * the Coinffeine market.
  *
  * The peers of the Coinffeine network are there to exchange bitcoins and fiat money in a
  * decentralized way. When one peer wants to perform a buy or sell operation, he emits
  * an order. Objects of this class represent the state of an order.
  *
  * @constructor      Private constructor to keep invariants
  * @param orderType  The type of order (bid or ask)
  * @param amount     The gross amount of bitcoins to bid or ask
  * @param price      The price per bitcoin
  * @param inMarket   Presence on the order book
  * @param exchanges  The exchanges that have been initiated to complete this order
  * @param log        Log of important order events
  */
case class ActiveOrder[C <: FiatCurrency] private (
    override val id: OrderId,
    override val orderType: OrderType,
    override val amount: Bitcoin.Amount,
    override val price: OrderPrice[C],
    override val inMarket: Boolean,
    override val exchanges: Map[ExchangeId, ActiveExchange[C]],
    override val log: ActivityLog[OrderStatus]) extends Order[C] {

  require(amount.isPositive, s"Orders should have a positive amount ($amount given)")

  def cancel(timestamp: DateTime): ActiveOrder[C] = {
    require(cancellable, "Cannot cancel %s with active exchanges %s".format(
      id, exchanges.values.filterNot(_.isCompleted).map(_.id)))
    copy(log = log.record(OrderStatus.Cancelled, timestamp))
  }
  def cancellable: Boolean = exchanges.values.forall(_.isCompleted)

  def becomeInMarket: ActiveOrder[C] = copy(inMarket = true)
  def becomeOffline: ActiveOrder[C] = copy(inMarket = false)

  override def status: OrderStatus = log.mostRecent.get.event

  def amounts: ActiveOrder.Amounts = ActiveOrder.Amounts.fromExchanges(amount, role, exchanges)

  def pendingOrderBookEntry: OrderBookEntry[C] =
    OrderBookEntry(id, orderType, amounts.pending, price)

  def shouldBeOnMarket: Boolean = !cancelled && amounts.pending.isPositive

  /** Create a new copy of this order with the given exchange. */
  def withExchange(exchange: ActiveExchange[C]): ActiveOrder[C] =
    if (exchanges.get(exchange.id).contains(exchange)) this
    else {
      val nextExchanges = exchanges + (exchange.id -> exchange)
      val nextAmounts = ActiveOrder.Amounts.fromExchanges(amount, role, nextExchanges)
      val timestamp = exchange.log.mostRecent.get.timestamp

      def recordProgressStart(log: ActivityLog[OrderStatus]) =
        if (amounts.progressMade || !nextAmounts.progressMade) log
        else log.record(OrderStatus.InProgress, timestamp)

      def recordCompletion(log: ActivityLog[OrderStatus]) =
        if (amounts.completed || !nextAmounts.completed) log
        else log.record(OrderStatus.Completed, timestamp)

      copy(
        inMarket = false,
        exchanges = nextExchanges,
        log = recordCompletion(recordProgressStart(log))
      )
    }
}

object ActiveOrder {
  case class Amounts(exchanged: Bitcoin.Amount,
                     exchanging: Bitcoin.Amount,
                     pending: Bitcoin.Amount) {
    require((exchanged + exchanging + pending).isPositive)
    def completed: Boolean = exchanging.isZero && pending.isZero
    def progressMade: Boolean = exchanging.isPositive || exchanged.isPositive
  }

  object Amounts {
    def fromExchanges[C <: FiatCurrency](amount: Bitcoin.Amount,
                                         role: Role,
                                         exchanges: Map[ExchangeId, ActiveExchange[C]]): Amounts = {
      def totalSum(exchanges: Iterable[ActiveExchange[C]]): Bitcoin.Amount = exchanges.map {
        case ex: SuccessfulExchange[_] => ex.progress.bitcoinsTransferred
        case ex: FailedExchange[_] => ex.progress.bitcoinsTransferred
        case ex => ex.amounts.exchangedBitcoin
      }.map(role.select).sum

      val exchangeGroups = exchanges.values.groupBy {
        case _: SuccessfulExchange[_] => 'exchanged
        case _: FailedExchange[_] => 'exchanged
        case _ => 'exchanging
      }.mapValues(totalSum).withDefaultValue(Bitcoin.Zero)

      ActiveOrder.Amounts(
        exchanged = exchangeGroups('exchanged),
        exchanging = exchangeGroups('exchanging),
        pending = amount - exchangeGroups('exchanged) - exchangeGroups('exchanging)
      )
    }
  }

  def apply[C <: FiatCurrency](id: OrderId,
                               orderType: OrderType,
                               amount: Bitcoin.Amount,
                               price: Price[C],
                               timestamp: DateTime = DateTime.now()): ActiveOrder[C] =
    apply(id, orderType, amount, LimitPrice(price), timestamp)

  def apply[C <: FiatCurrency](id: OrderId,
                               orderType: OrderType,
                               amount: Bitcoin.Amount,
                               price: OrderPrice[C],
                               timestamp: DateTime): ActiveOrder[C] = {
    val log = ActivityLog(OrderStatus.NotStarted, timestamp)
    ActiveOrder(id, orderType, amount, price, inMarket = false, exchanges = Map.empty, log)
  }

  /** Creates a limit order with a random identifier. */
  def randomLimit[C <: FiatCurrency](orderType: OrderType,
                                     amount: Bitcoin.Amount,
                                     price: Price[C],
                                     timestamp: DateTime = DateTime.now()) =
    random(orderType, amount, LimitPrice(price), timestamp)

  /** Creates a market price order with a random identifier. */
  def randomMarketPrice[C <: FiatCurrency](orderType: OrderType,
                                           amount: Bitcoin.Amount,
                                           currency: C,
                                           timestamp: DateTime = DateTime.now()) =
    random(orderType, amount, MarketPrice(currency), timestamp)

  /** Creates a market price order with a random identifier. */
  def random[C <: FiatCurrency](orderType: OrderType,
                                amount: Bitcoin.Amount,
                                price: OrderPrice[C],
                                timestamp: DateTime = DateTime.now()) =
    ActiveOrder(OrderId.random(), orderType, amount, price, timestamp)
}
