package coinffeine.model.order

import org.joda.time.DateTime

import coinffeine.model.ActivityLog
import coinffeine.model.currency._
import coinffeine.model.exchange.{Exchange, ExchangeId, Role}

trait Order[C <: FiatCurrency] {

  def id: OrderId
  def orderType: OrderType
  def amount: Bitcoin.Amount
  def price: OrderPrice[C]
  def inMarket: Boolean
  def exchanges: Map[ExchangeId, Exchange[C]]
  def log: ActivityLog[OrderStatus]
  def status: OrderStatus

  def role = Role.fromOrderType(orderType)
  def createdOn: DateTime = log.activities.head.timestamp
  def cancelled: Boolean = log.lastTime(_ == OrderStatus.Cancelled).isDefined

  /** Timestamp of the last recorded change */
  def lastChange: DateTime = log.mostRecent.get.timestamp

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

  private def totalSum[A <: Currency](zero: CurrencyAmount[A])
                                     (f: Exchange[C] => CurrencyAmount[A]): CurrencyAmount[A] =
    exchanges.values.map(f).foldLeft(zero)(_ + _)
}
