package coinffeine.gui.application.properties

import scalafx.beans.property._
import scalafx.collections.ObservableBuffer

import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency.Bitcoin
import coinffeine.model.exchange.AnyExchange
import coinffeine.model.market._

class OrderProperties(initialValue: AnyCurrencyOrder) extends OperationProperties {
  val orderProperty = new ObjectProperty[AnyCurrencyOrder](this, "source", initialValue)
  val orderIdProperty = new ObjectProperty[OrderId](this, "id", initialValue.id)
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", initialValue.orderType)
  val orderStatusProperty = new ObjectProperty[OrderStatus](this, "status", initialValue.status)

  val exchanges = new ObservableBuffer[ExchangeProperties]
  updateExchanges(initialValue.exchanges.values.toSeq)

  override val sourceProperty =
    orderProperty.delegate.map(order => order: AnyRef).toReadOnlyProperty

  override val idProperty =
    orderIdProperty.delegate.mapToString(_.value).toReadOnlyProperty

  override val operationTypeProperty =
    orderTypeProperty.delegate.mapToString(_.name.capitalize).toReadOnlyProperty

  override val statusProperty =
    orderStatusProperty.delegate.mapToString(_.name.capitalize).toReadOnlyProperty

  override val isCancellable =
    orderStatusProperty.delegate.mapToBool(_.isActive).toReadOnlyProperty

  override val amountProperty = new ObjectProperty[Bitcoin.Amount](this, "amount", initialValue.amount)
  override val priceProperty = new ObjectProperty[AnyOrderPrice](this, "price", initialValue.price)
  override val progressProperty = new DoubleProperty(this, "progress", initialValue.progress)

  def update(order: AnyCurrencyOrder): Unit = {
    orderProperty.value = order
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
    newExchanges
      .filter(_.isStarted)
      .map(ex => new ExchangeProperties(ex)).foreach(exchanges.add)
  }
}
