package coinffeine.gui.application.properties

import scalafx.beans.property._
import scalafx.collections.ObservableBuffer

import org.joda.time.DateTime

import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.exchange.Exchange
import coinffeine.model.order._

class MutableOrderProperties(initialValue: Order) extends OrderProperties {

  override val orderProperty = new ObjectProperty[Order](this, "source", initialValue)

  override val idProperty = new ReadOnlyObjectProperty[OrderId](this, "id", initialValue.id)

  override val typeProperty = new ReadOnlyObjectProperty[OrderType](
    this, "orderType", initialValue.orderType)

  override val createdOnProperty = new ReadOnlyObjectProperty[DateTime](
    this, "createdOn", initialValue.createdOn)

  override val exchanges = new ObservableBuffer[ExchangeProperties]
  updateExchanges(initialValue.exchanges.values.toSeq)

  override val statusProperty =
    orderProperty.delegate.map(_.status).toReadOnlyProperty

  override val isCancellable = statusProperty.delegate.map(_.isActive).toBool.toReadOnlyProperty

  override val amountProperty =
    new ReadOnlyObjectProperty[BitcoinAmount](this, "amount", initialValue.amount)

  override val priceProperty =
    new ReadOnlyObjectProperty[OrderPrice](this, "price", initialValue.price)

  override val progressProperty = orderProperty.delegate.mapToDouble(_.progress).toReadOnlyProperty

  def update(order: Order): Unit = {
    orderProperty.value = order
    updateExchanges(order.exchanges.values.toSeq)
  }

  private def updateExchanges(newExchanges: Seq[Exchange]): Unit = {
    exchanges.clear()
    newExchanges
      .filter(_.isStarted)
      .map(ex => new ExchangeProperties(ex)).foreach(exchanges.add)
  }
}
