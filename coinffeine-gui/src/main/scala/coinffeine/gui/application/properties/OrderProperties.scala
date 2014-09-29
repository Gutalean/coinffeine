package coinffeine.gui.application.properties

import scalafx.beans.property._

import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.market._

class OrderProperties(order: AnyCurrencyOrder) extends OperationProperties {
  val orderIdProperty = new ObjectProperty[OrderId](this, "id", order.id)
  val orderTypeProperty = new ObjectProperty[OrderType](this, "orderType", order.orderType)
  val orderStatusProperty = new ObjectProperty[OrderStatus](this, "status", order.status)

  override val idProperty =
    orderIdProperty.delegate.mapToString(_.value).toReadOnlyProperty

  override val operationTypeProperty =
    orderTypeProperty.delegate.mapToString(_.name).toReadOnlyProperty

  override val statusProperty =
    orderStatusProperty.delegate.mapToString(_.name).toReadOnlyProperty

  override val isCancellable =
    orderStatusProperty.delegate.mapToBool(_.isCancellable).toReadOnlyProperty

  override val amountProperty = new ObjectProperty[Bitcoin.Amount](this, "amount", order.amount)
  override val priceProperty = new ObjectProperty[Price[_ <: FiatCurrency]](this, "price", order.price)
  override val progressProperty = new DoubleProperty(this, "progress", order.progress)

  def update(order: AnyCurrencyOrder): Unit = {
    updateStatus(order.status)
    updateProgress(order.progress)
  }

  def updateStatus(newStatus: OrderStatus): Unit = {
    orderStatusProperty.set(newStatus)
  }

  def updateProgress(newProgress: Double): Unit = {
    progressProperty.set(newProgress)
  }

}
