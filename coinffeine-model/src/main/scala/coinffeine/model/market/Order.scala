package coinffeine.model.market

import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency._
import coinffeine.model.exchange._

/** An order represents a process initiated by a peer to bid (buy) or ask(sell) bitcoins in
  * the Coinffeine market.
  *
  * The peers of the Coinffeine network are there to exchange bitcoins and fiat money in a
  * decentralized way. When one peer wants to perform a buy or sell operation, he emits
  * an order. Objects of this class represent the state of an order.
  *
  * @param orderType  The type of order (bid or ask)
  * @param status     The current status of the order
  * @param amount     The gross amount of bitcoins to bid or ask
  * @param price      The price per bitcoin
  * @param exchanges  The exchanges that have been initiated to complete this order
  */
case class Order[C <: FiatCurrency](
    id: OrderId,
    orderType: OrderType,
    status: OrderStatus,
    amount: BitcoinAmount,
    price: Price[C],
    exchanges: Map[ExchangeId, AnyStateExchange[C]]) {

  def amounts: Order.Amounts = {
    val role = Role.fromOrderType(orderType)

    def totalSum(exchanges: Iterable[AnyStateExchange[C]]): BitcoinAmount =
      exchanges.map(ex => role.select(ex.amounts.exchangedBitcoin))
        .foldLeft(Bitcoin.Zero)(_ + _)

    val exchangeGroups = exchanges.values.groupBy(_.state match {
      case _: Exchange.Successful[_] => 'exchanged
      case _: Exchange.Exchanging[_] => 'exchanging
      case _ => 'other
    }).mapValues(totalSum).withDefaultValue(Bitcoin.Zero)

    Order.Amounts(
      exchanged = exchangeGroups('exchanged),
      exchanging = exchangeGroups('exchanging),
      pending = amount - exchangeGroups('exchanged) - exchangeGroups('exchanging)
    )
  }

  /** Create a new copy of this order with the given status. */
  def withStatus(newStatus: OrderStatus): Order[C] = copy(status = newStatus)

  /** Create a new copy of this order with the given exchange. */
  def withExchange(exchange: AnyStateExchange[C]): Order[C] =
    copy(exchanges = exchanges + (exchange.id -> exchange))

  /** Retrieve the total amount of bitcoins that were already transferred.
    *
    * This count comprise those bitcoins belonging to exchanges that have been completed and
    * exchanges that are in course. That doesn't include the deposits.
    *
    * @return The amount of bitcoins that have been transferred
    */
  def bitcoinsTransferred: BitcoinAmount =
    totalSum(Bitcoin.Zero)(e => e.progress.bitcoinsTransferred)

  /** Retrieve the total amount of fiat money transferred.
    *
    * @return The amount of fiat money that has been transferred.
    */
  def fiatTransferred: FiatAmount =
    totalSum(CurrencyAmount.zero(price.currency))(e => e.progress.fiatTransferred)

  /** Retrieve the progress of this order.
    *
    * The progress is measured with a double value in range [0.0, 1.0].
    *
    * @return
    */
  def progress: Double = (bitcoinsTransferred.value / amount.value).toDouble

  private def totalSum[A <: Currency](
      zero: CurrencyAmount[A])(f: AnyStateExchange[C] => CurrencyAmount[A]): CurrencyAmount[A] =
    exchanges.values.map(f).foldLeft(zero)(_ + _)
}

object Order {
  case class Amounts(exchanged: BitcoinAmount, exchanging: BitcoinAmount, pending: BitcoinAmount)

  def apply[C <: FiatCurrency](id: OrderId,
                               orderType: OrderType,
                               amount: BitcoinAmount,
                               price: Price[C]): Order[C] =
    Order(id, orderType, status = OfflineOrder, amount, price, exchanges = Map.empty)

  def apply[C <: FiatCurrency](orderType: OrderType,
                               amount: BitcoinAmount,
                               price: Price[C]): Order[C] =
    Order(OrderId.random(), orderType, amount, price)
}
