package coinffeine.gui.application.properties

import scalafx.beans.property._
import scalafx.collections.ObservableBuffer

import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency.Bitcoin
import coinffeine.model.exchange.AnyExchange
import coinffeine.model.market._
import org.joda.time.DateTime

class MutableOrderProperties(initialValue: AnyCurrencyActiveOrder) extends OrderProperties {

  override val orderProperty = new ObjectProperty[AnyCurrencyActiveOrder](this, "source", initialValue)

  override val idProperty = new ReadOnlyObjectProperty[OrderId](this, "id", initialValue.id)

  override val typeProperty = new ReadOnlyObjectProperty[OrderType](
    this, "orderType", initialValue.orderType)

  override val createdOnProperty = new ReadOnlyObjectProperty[DateTime](
    this, "createdOn", initialValue.log.activities.head.timestamp)

  override val exchanges = new ObservableBuffer[ExchangeProperties]
  updateExchanges(initialValue.exchanges.values.toSeq)

  override val statusProperty =
    orderProperty.delegate.map(_.status).toReadOnlyProperty

  override val isCancellable = statusProperty.delegate.mapToBool(_.isActive).toReadOnlyProperty

  override val amountProperty =
    new ReadOnlyObjectProperty[Bitcoin.Amount](this, "amount", initialValue.amount)

  override val priceProperty =
    new ReadOnlyObjectProperty[AnyOrderPrice](this, "price", initialValue.price)

  override val progressProperty = orderProperty.delegate.mapToDouble(_.progress).toReadOnlyProperty

  def update(order: AnyCurrencyActiveOrder): Unit = {
    orderProperty.value = order
    updateExchanges(order.exchanges.values.toSeq)
  }

  private def updateExchanges(newExchanges: Seq[AnyExchange]): Unit = {
    exchanges.clear()
    newExchanges
      .filter(_.isStarted)
      .map(ex => new ExchangeProperties(ex)).foreach(exchanges.add)
  }
}
