package coinffeine.model.market

import org.joda.time.DateTime

import coinffeine.model.ActivityLog
import coinffeine.model.currency._
import coinffeine.model.exchange._

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
case class Order[C <: FiatCurrency] private (
    id: OrderId,
    orderType: OrderType,
    amount: Bitcoin.Amount,
    price: OrderPrice[C],
    inMarket: Boolean,
    exchanges: Map[ExchangeId, Exchange[C]],
    log: ActivityLog[OrderStatus]) {

  require(amount.isPositive, s"Orders should have a positive amount ($amount given)")

  val role = Role.fromOrderType(orderType)
  lazy val amounts: Order.Amounts = Order.Amounts.fromExchanges(amount, role, exchanges)

  def cancel: Order[C] = copy(log = log.record(CancelledOrder))
  def becomeInMarket: Order[C] = copy(inMarket = true)
  def becomeOffline: Order[C] = copy(inMarket = false)

  def status: OrderStatus = log.mostRecent.get.event
  def cancelled: Boolean = log.lastTime(_ == CancelledOrder).isDefined

  /** Create a new copy of this order with the given exchange. */
  def withExchange(exchange: Exchange[C]): Order[C] =
    if (exchanges.get(exchange.id).contains(exchange)) this
    else {
      val nextExchanges = exchanges + (exchange.id -> exchange)
      val nextAmounts = Order.Amounts.fromExchanges(amount, role, nextExchanges)
      val timestamp = exchange.log.mostRecent.get.timestamp

      def recordProgressStart(log: ActivityLog[OrderStatus]) =
        if (amounts.progressMade || !nextAmounts.progressMade) log
        else log.record(InProgressOrder, timestamp)

      def recordCompletion(log: ActivityLog[OrderStatus]) =
        if (amounts.completed || !nextAmounts.completed) log
        else log.record(CompletedOrder, timestamp)

      copy(
        inMarket = false,
        exchanges = nextExchanges,
        log = recordCompletion(recordProgressStart(log))
      )
    }

  /** Retrieve the total amount of bitcoins that were already transferred.
    *
    * This count comprise those bitcoins belonging to exchanges that have been completed and
    * exchanges that are in course. That doesn't include the deposits.
    *
    * @return The amount of bitcoins that have been transferred
    */
  def bitcoinsTransferred: Bitcoin.Amount =
    totalSum(Bitcoin.Zero)(e => role.select(e.progress.bitcoinsTransferred))

  /** Retrieve the progress of this order.
    *
    * The progress is measured with a double value in range [0.0, 1.0].
    *
    * @return
    */
  def progress: Double = (bitcoinsTransferred.value / amount.value).toDouble

  def pendingOrderBookEntry: OrderBookEntry[C] =
    OrderBookEntry(id, orderType, amounts.pending, price)

  def shouldBeOnMarket: Boolean = !cancelled && amounts.pending.isPositive

  /** Timestamp of the last recorded change */
  def lastChange: DateTime = log.mostRecent.get.timestamp

  private def totalSum[A <: Currency](
      zero: CurrencyAmount[A])(f: Exchange[C] => CurrencyAmount[A]): CurrencyAmount[A] =
    exchanges.values.map(f).foldLeft(zero)(_ + _)
}

object Order {
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
                                         exchanges: Map[ExchangeId, Exchange[C]]): Amounts = {
      def totalSum(exchanges: Iterable[Exchange[C]]): Bitcoin.Amount =
        exchanges.map(ex => role.select(ex.amounts.exchangedBitcoin)).sum

      val exchangeGroups = exchanges.values.groupBy {
        case _: SuccessfulExchange[_] => 'exchanged
        case _: FailedExchange[_] => 'other
        case _ => 'exchanging
      }.mapValues(totalSum).withDefaultValue(Bitcoin.Zero)

      Order.Amounts(
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
                               timestamp: DateTime = DateTime.now()): Order[C] =
    apply(id, orderType, amount, LimitPrice(price), timestamp)

  def apply[C <: FiatCurrency](id: OrderId,
                               orderType: OrderType,
                               amount: Bitcoin.Amount,
                               price: OrderPrice[C],
                               timestamp: DateTime): Order[C] = {
    val log = ActivityLog(NotStartedOrder, timestamp)
    Order(id, orderType, amount, price, inMarket = false, exchanges = Map.empty, log)
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
    Order(OrderId.random(), orderType, amount, price, timestamp)
}
