package coinffeine.model.order

import org.joda.time.DateTime

import coinffeine.model.ActivityLog
import coinffeine.model.currency._
import coinffeine.model.exchange.{Exchange, ExchangeId, Role}

trait Order {

  def id: OrderId
  def orderType: OrderType
  def amount: BitcoinAmount
  def price: OrderPrice
  def inMarket: Boolean
  def exchanges: Map[ExchangeId, Exchange]
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
  def bitcoinsTransferred: BitcoinAmount =
    exchanges.values.map(e => role.select(e.progress.bitcoinsTransferred)).sum

  /** Retrieve the progress of this order.
    *
    * The progress is measured with a double value in range [0.0, 1.0].
    *
    * @return
    */
  def progress: Double = (bitcoinsTransferred.value / amount.value).toDouble

  /** Whether the order can be cancelled */
  def cancellable: Boolean
}
