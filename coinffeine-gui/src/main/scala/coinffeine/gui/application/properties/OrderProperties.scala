package coinffeine.gui.application.properties

import scalafx.beans.property._
import scalafx.collections.ObservableBuffer

import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency.Bitcoin
import coinffeine.model.exchange.AnyExchange
import coinffeine.model.market._

class OrderProperties(order: AnyCurrencyOrder) extends OperationProperties {
  val sourceOrderProperty = new ObjectProperty[AnyCurrencyOrder](this, "source", order)
  val orderIdProperty = new ObjectProperty[OrderId](this, "id", order.id)
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", order.orderType)
  val orderStatusProperty = new ObjectProperty[OrderStatus](this, "status", order.status)

  val exchanges = new ObservableBuffer[ExchangeProperties]
  updateExchanges(order.exchanges.values.toSeq)

  override val sourceProperty =
    sourceOrderProperty.delegate.map(order => order: AnyRef).toReadOnlyProperty

  override val idProperty =
    orderIdProperty.delegate.mapToString(_.value).toReadOnlyProperty

  override val operationTypeProperty =
    orderTypeProperty.delegate.mapToString(_.name.capitalize).toReadOnlyProperty

  override val statusProperty =
    orderStatusProperty.delegate.mapToString(_.name.capitalize).toReadOnlyProperty

  override val isCancellable =
    orderStatusProperty.delegate.mapToBool(_.isActive).toReadOnlyProperty

  override val amountProperty = new ObjectProperty[Bitcoin.Amount](this, "amount", order.amount)
  override val priceProperty = new ObjectProperty[AnyOrderPrice](this, "price", order.price)
  override val progressProperty = new DoubleProperty(this, "progress", order.progress)

  def update(order: AnyCurrencyOrder): Unit = {
    sourceOrderProperty.value = order
    updateStatus(order.status)
    updateProgress(order.progress)
    updateExchanges(order.exchanges.values.toSeq)
  }

  def updateStatus(newStatus: OrderStatus): Unit = {
    orderStatusProperty.set(newStatus)
  }

  def updateProgress(newProgress: Double): Unit = {
    progressProperty.set(newProgress)
  }

  def updateExchanges(newExchanges: Seq[AnyExchange]): Unit = {
    exchanges.clear()
    newExchanges.map(ex => new ExchangeProperties(ex)).foreach(exchanges.add)
  }
}
