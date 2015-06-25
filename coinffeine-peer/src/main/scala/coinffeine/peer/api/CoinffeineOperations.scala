package coinffeine.peer.api

import scala.concurrent.Future

import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.operations.OperationsProperties
import coinffeine.model.order._

trait CoinffeineOperations extends OperationsProperties {

  /** Submit an order to buy bitcoins.
    *
    * @param amount  Amount to buy
    * @param price   Price in fiat
    * @return        A new order if submitted successfully
    */
  def submitBuyOrder(amount: BitcoinAmount, price: Price): Future[Order] =
    submitOrder(OrderRequest(Bid, amount, LimitPrice(price)))

  /** Submit an order to sell bitcoins.
    *
    * @param amount  Amount to sell
    * @param price   Price in fiat
    * @return        A new order if submitted successfully
    */
  def submitSellOrder(amount: BitcoinAmount, price: Price): Future[Order] =
    submitOrder(OrderRequest(Ask, amount, LimitPrice(price)))

  /** Submit an order. */
  def submitOrder(order: OrderRequest): Future[Order]

  /** Cancel an order
    *
    * @param order The order id to be cancelled
    */
  def cancelOrder(order: OrderId): Unit
}
