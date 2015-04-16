package coinffeine.gui.application.properties

import scalafx.beans.property._
import scalafx.collections.ObservableBuffer

import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency.Bitcoin
import coinffeine.model.exchange.AnyExchange
import coinffeine.model.market._

class OrderProperties(initialValue: AnyCurrencyOrder) {
  val orderProperty = new ObjectProperty[AnyCurrencyOrder](this, "source", initialValue)
  val orderIdProperty = new ObjectProperty[OrderId](this, "id", initialValue.id)
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", initialValue.orderType)
  val orderStatusProperty = new ObjectProperty[OrderStatus](this, "status", initialValue.status)
  val orderProgressProperty = new DoubleProperty(this, "progress", initialValue.progress)

  val exchanges = new ObservableBuffer[ExchangeProperties]
  updateExchanges(initialValue.exchanges.values.toSeq)

  val sourceProperty: ReadOnlyObjectProperty[AnyRef] =
    orderProperty.delegate.map(order => order: AnyRef).toReadOnlyProperty

  val idProperty: ReadOnlyStringProperty =
    orderIdProperty.delegate.mapToString(_.value).toReadOnlyProperty

  val operationTypeProperty: ReadOnlyStringProperty =
    orderTypeProperty.delegate.mapToString(_.name.capitalize).toReadOnlyProperty

  val statusProperty: ReadOnlyStringProperty =
    orderStatusProperty.delegate.mapToString(_.name.capitalize).toReadOnlyProperty

  val isCancellable: ReadOnlyBooleanProperty =
    orderStatusProperty.delegate.mapToBool(_.isActive).toReadOnlyProperty

  val amountProperty: ReadOnlyObjectProperty[Bitcoin.Amount] =
    new ObjectProperty[Bitcoin.Amount](this, "amount", initialValue.amount)

  val priceProperty: ReadOnlyObjectProperty[AnyOrderPrice] =
    new ObjectProperty[AnyOrderPrice](this, "price", initialValue.price)

  val progressProperty: ReadOnlyDoubleProperty = orderProgressProperty

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
    orderProgressProperty.set(newProgress)
  }

  def updateExchanges(newExchanges: Seq[AnyExchange]): Unit = {
    exchanges.clear()
    newExchanges
      .filter(_.isStarted)
      .map(ex => new ExchangeProperties(ex)).foreach(exchanges.add)
  }
}
