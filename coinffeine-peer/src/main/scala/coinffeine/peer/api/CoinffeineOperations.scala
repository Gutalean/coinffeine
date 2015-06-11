package coinffeine.peer.api

import scala.concurrent.Future

import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.operations.OperationsProperties
import coinffeine.model.order._

trait CoinffeineOperations extends OperationsProperties {

  /** Submit an order to buy bitcoins.
    *
    * @param amount  Amount to buy
    * @param price   Price in fiat
    * @return        A new order if submitted successfully
    */
  def submitBuyOrder[C <: FiatCurrency](amount: Bitcoin.Amount,
                                        price: Price[C]): Future[Order[C]] =
    submitOrder(OrderRequest(Bid, amount, LimitPrice(price)))

  /** Submit an order to sell bitcoins.
    *
    * @param amount  Amount to sell
    * @param price   Price in fiat
    * @return        A new order if submitted successfully
    */
  def submitSellOrder[C <: FiatCurrency](amount: Bitcoin.Amount,
                                         price: Price[C]): Future[Order[C]] =
    submitOrder(OrderRequest(Ask, amount, LimitPrice(price)))

  /** Submit an order. */
  def submitOrder[C <: FiatCurrency](order: OrderRequest[C]): Future[Order[C]]

  /** Cancel an order
    *
    * @param order The order id to be cancelled
    */
  def cancelOrder(order: OrderId): Unit
}
