package coinffeine.peer.api

import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.exchange.AnyExchange
import coinffeine.model.market._
import coinffeine.model.network.{PeerId, CoinffeineNetworkProperties}
import coinffeine.model.properties.Property

/** Represents how the app takes part on the P2P network */
trait CoinffeineNetwork extends CoinffeineNetworkProperties {

  def orders: Set[Order[c] forSome { type c <: FiatCurrency }]

  def exchanges: Set[AnyExchange] = orders.flatMap[AnyExchange, Set[AnyExchange]](_.exchanges.values)

  /** Submit an order to buy bitcoins.
    *
    * @param amount  Amount to buy
    * @param price   Price in fiat
    * @return        A new exchange if submitted successfully
    */
  def submitBuyOrder[C <: FiatCurrency](amount: BitcoinAmount, price: Price[C]): Order[C] =
    submitOrder(Order(Bid, amount, price))

  /** Submit an order to sell bitcoins.
    *
    * @param amount  Amount to sell
    * @param price   Price in fiat
    * @return        A new exchange if submitted successfully
    */
  def submitSellOrder[C <: FiatCurrency](amount: BitcoinAmount, price: Price[C]): Order[C] =
    submitOrder(Order(Ask, amount, price))

  /** Submit an order. */
  def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C]

  /** Cancel an order
    *
    * @param order The order id to be cancelled
    * @param reason A user friendly message that explains why the order is being cancelled
    */
  def cancelOrder(order: OrderId, reason: String): Unit
}

object CoinffeineNetwork {

  case class ConnectException(cause: Throwable)
    extends Exception("Cannot connect to the P2P network", cause)
}
