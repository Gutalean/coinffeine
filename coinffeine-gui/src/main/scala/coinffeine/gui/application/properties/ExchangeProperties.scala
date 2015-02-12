package coinffeine.gui.application.properties

import scalafx.beans.property._

import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency.Bitcoin
import coinffeine.model.exchange.{AnyExchange, ExchangeId}
import coinffeine.model.market.{AnyOrderPrice, LimitPrice}

class ExchangeProperties(exchange: AnyExchange) extends OperationProperties {

  val exchangeSourceProperty: ObjectProperty[AnyExchange] =
    new ObjectProperty(this, "source", exchange)

  val exchangeIdProperty: ObjectProperty[ExchangeId] =
    new ObjectProperty(this, "id", exchange.id)

  val exchangeProgressProperty: DoubleProperty =
    new DoubleProperty(this, "progress", progressOf(exchange))

  override val sourceProperty =
    exchangeSourceProperty.delegate.map(ex => ex: AnyRef).toReadOnlyProperty

  override val idProperty =
    exchangeIdProperty.delegate.mapToString(_.value).toReadOnlyProperty

  override val isCancellable: ReadOnlyBooleanProperty =
    new BooleanProperty(this, "isCancellable", false)

  override val amountProperty: ReadOnlyObjectProperty[Bitcoin.Amount] =
    new ObjectProperty(this, "amount", exchange.role.select(exchange.amounts.exchangedBitcoin))

  override val statusProperty: ReadOnlyStringProperty =
    new StringProperty(this, "status", exchange.status.capitalize)

  override val progressProperty: ReadOnlyDoubleProperty = exchangeProgressProperty

  override val priceProperty: ReadOnlyObjectProperty[AnyOrderPrice] =
    new ObjectProperty(this, "price", LimitPrice(exchange.amounts.price(exchange.role)))

  override val operationTypeProperty: ReadOnlyStringProperty =
    new StringProperty(this, "opType", "Exchange")

  def updateProgress(exchange: AnyExchange): Unit = {
    exchangeProgressProperty.value = progressOf(exchange)
  }

  private def progressOf(exchange: AnyExchange): Double = {
    val done = exchange.role.select(exchange.progress.bitcoinsTransferred).value
    val total = exchange.role.select(exchange.amounts.exchangedBitcoin).value
    (done / total).toDouble
  }
}
