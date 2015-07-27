package coinffeine.gui.application.properties

import scalafx.beans.property._

import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.exchange.{Exchange, ExchangeId}
import coinffeine.model.order.Price

class ExchangeProperties(exchange: Exchange) {

  val exchangeSourceProperty: ObjectProperty[Exchange] =
    new ObjectProperty(this, "source", exchange)

  val exchangeIdProperty: ObjectProperty[ExchangeId] =
    new ObjectProperty(this, "id", exchange.id)

  val exchangeProgressProperty: DoubleProperty =
    new DoubleProperty(this, "progress", progressOf(exchange))

  val sourceProperty: ReadOnlyObjectProperty[AnyRef] =
    exchangeSourceProperty.delegate.map(ex => ex: AnyRef).toReadOnlyProperty

  val idProperty: ReadOnlyStringProperty =
    exchangeIdProperty.delegate.map(_.value).toStr.toReadOnlyProperty

  val isCancellable: ReadOnlyBooleanProperty =
    new BooleanProperty(this, "isCancellable", false)

  val amountProperty: ReadOnlyObjectProperty[BitcoinAmount] =
    new ObjectProperty(this, "amount", exchange.role.select(exchange.exchangedBitcoin))

  val statusProperty: ReadOnlyStringProperty =
    new StringProperty(this, "status", exchange.status.name.capitalize)

  val progressProperty: ReadOnlyDoubleProperty = exchangeProgressProperty

  val priceProperty: ReadOnlyObjectProperty[Price] =
    new ObjectProperty(this, "price", Price.whenExchanging(
      exchange.role.select(exchange.exchangedBitcoin),
      exchange.role.select(exchange.exchangedFiat)))

  val operationTypeProperty: ReadOnlyStringProperty =
    new StringProperty(this, "opType", "Exchange")

  def updateProgress(exchange: Exchange): Unit = {
    exchangeProgressProperty.value = progressOf(exchange)
  }

  private def progressOf(exchange: Exchange): Double = {
    val done = exchange.role.select(exchange.progress.bitcoinsTransferred).value
    val total = exchange.role.select(exchange.exchangedBitcoin).value
    (done / total).toDouble
  }
}
