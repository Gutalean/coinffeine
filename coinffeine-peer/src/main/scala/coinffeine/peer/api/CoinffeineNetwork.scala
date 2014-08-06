package coinffeine.peer.api

import scala.concurrent.Future

import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange
import coinffeine.model.market._

/** Represents how the app takes part on the P2P network */
trait CoinffeineNetwork {

  def status: CoinffeineNetwork.Status

  /** Start connection with the network.
    *
    * @return The connected status in case of success or ConnectException otherwise
    */
  def connect(): Future[CoinffeineNetwork.Connected.type]

  /** Disconnect from the network.
    *
    * @return A future to be resolved when actually disconnected from the network.
    */
  def disconnect(): Future[CoinffeineNetwork.Disconnected.type]

  def orders: Set[Order[FiatCurrency]]

  def exchanges: Set[Exchange[FiatCurrency]] = orders.flatMap(_.exchanges.values)

  /** Submit an order to buy bitcoins.
    *
    * @param btcAmount           Amount to buy
    * @param fiatAmount          Fiat money to use
    * @return                    A new exchange if submitted successfully
    */
  def submitBuyOrder[C <: FiatCurrency](btcAmount: BitcoinAmount,
                                        fiatAmount: CurrencyAmount[C]): Order[C] =
    submitOrder(Order(Bid, btcAmount, fiatAmount))

  /** Submit an order to sell bitcoins.
    *
    * @param btcAmount           Amount to sell
    * @param fiatAmount          Fiat money to use
    * @return                    A new exchange if submitted successfully
    */
  def submitSellOrder[C <: FiatCurrency](btcAmount: BitcoinAmount,
                                         fiatAmount: CurrencyAmount[C]): Order[C] =
    submitOrder(Order(Ask, btcAmount, fiatAmount))

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

  sealed trait Status
  case object Disconnected extends Status
  case object Connected extends Status
  case object Connecting extends Status

  case class ConnectException(cause: Throwable)
    extends Exception("Cannot connect to the P2P network", cause)
}
