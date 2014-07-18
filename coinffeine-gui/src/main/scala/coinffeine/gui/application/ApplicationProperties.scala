package coinffeine.gui.application

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.collections.ObservableBuffer

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.util.FxEventHandler
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount}
import coinffeine.model.market.OrderId
import coinffeine.peer.api.event._
import coinffeine.peer.api.{CoinffeineApp, EventHandler}

class ApplicationProperties(app: CoinffeineApp) {

  val ordersProperty = ObservableBuffer[OrderProperties]()

  def walletBalanceProperty: ReadOnlyObjectProperty[Option[BitcoinAmount]] =
    _walletBalanceProperty

  def fiatBalanceProperty: ReadOnlyObjectProperty[Option[CurrencyAmount[Euro.type]]] =
    _fiatBalanceProperty

  private val _walletBalanceProperty = new ObjectProperty[Option[BitcoinAmount]](
    this, "walletBalance", None)

  private val _fiatBalanceProperty = new ObjectProperty[Option[CurrencyAmount[Euro.type]]](
    this, "fiatBalance", app.paymentProcessor.currentBalance())

  private val eventHandler: EventHandler = FxEventHandler {

    case OrderSubmittedEvent(order) =>
      require(!orderExist(order.id), s"Duplicated OrderId: ${order.id}")
      ordersProperty.add(OrderProperties(order))

    case OrderUpdatedEvent(order) =>
      withOrder(order.id) { (o, _) => o.update(order) }

    case OrderCancelledEvent(orderId) =>
      withOrder(orderId) { (_, i) => ordersProperty.remove(i) }

    case WalletBalanceChangeEvent(newBalance) =>
      _walletBalanceProperty.set(Some(newBalance))

    case FiatBalanceChangeEvent(newBalance @ CurrencyAmount(_, Euro)) =>
      _fiatBalanceProperty.set(Some(newBalance.asInstanceOf[CurrencyAmount[Euro.type]]))
  }

  private def orderExist(orderId: OrderId): Boolean = ordersProperty.exists(_.order.id == orderId)

  private def withOrder(orderId: OrderId)(f: (OrderProperties, Int) => Unit): Unit =
    ordersProperty.zipWithIndex.foreach { case (prop, index) =>
      if (prop.order.id == orderId) { f(prop, index) }
    }

  app.observe(eventHandler)
}
