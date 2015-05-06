package coinffeine.peer.api

import scala.concurrent.Future

import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.market._
import coinffeine.model.network.CoinffeineNetworkProperties

/** Represents how the app takes part on the P2P network */
trait CoinffeineNetwork extends CoinffeineNetworkProperties {

  /** Submit an order to buy bitcoins.
    *
    * @param amount  Amount to buy
    * @param price   Price in fiat
    * @return        A new order if submitted successfully
    */
  def submitBuyOrder[C <: FiatCurrency](amount: Bitcoin.Amount,
                                        price: Price[C]): Future[ActiveOrder[C]] =
    submitOrder(OrderRequest(Bid, amount, LimitPrice(price)))

  /** Submit an order to sell bitcoins.
    *
    * @param amount  Amount to sell
    * @param price   Price in fiat
    * @return        A new order if submitted successfully
    */
  def submitSellOrder[C <: FiatCurrency](amount: Bitcoin.Amount,
                                         price: Price[C]): Future[ActiveOrder[C]] =
    submitOrder(OrderRequest(Ask, amount, LimitPrice(price)))

  /** Submit an order. */
  def submitOrder[C <: FiatCurrency](order: OrderRequest[C]): Future[ActiveOrder[C]]

  /** Cancel an order
    *
    * @param order The order id to be cancelled
    */
  def cancelOrder(order: OrderId): Unit
}

object CoinffeineNetwork {

  case class ConnectException(cause: Throwable)
    extends Exception("Cannot connect to the P2P network", cause)
}
