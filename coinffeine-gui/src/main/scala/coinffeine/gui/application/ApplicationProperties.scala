package coinffeine.gui.application

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.collections.ObservableBuffer

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.util.FxEventHandler
import coinffeine.model.currency.Currency.{Bitcoin, Euro}
import coinffeine.model.market.OrderId
import coinffeine.peer.api.event._
import coinffeine.peer.api.{CoinffeineApp, EventHandler}

class ApplicationProperties(app: CoinffeineApp) {
  type BitcoinBalance = Balance[Bitcoin.type]
  type FiatBalance = Balance[Euro.type]

  val ordersProperty = ObservableBuffer[OrderProperties]()

  def walletBalanceProperty: ReadOnlyObjectProperty[Option[BitcoinBalance]] = _walletBalanceProperty
  def fiatBalanceProperty: ReadOnlyObjectProperty[Option[FiatBalance]] = _fiatBalanceProperty

  private val _walletBalanceProperty = new ObjectProperty[Option[BitcoinBalance]](
    this, "walletBalance", Some(Balance(app.wallet.currentBalance())))

  private val _fiatBalanceProperty =
    new ObjectProperty[Option[FiatBalance]](this, "fiatBalance", initialFiatBalance)

  private def initialFiatBalance: Option[FiatBalance] =
    app.paymentProcessor.currentBalance().map { balance => Balance(balance.totalFunds) }

  private val eventHandler: EventHandler = FxEventHandler {

    case OrderSubmittedEvent(order) =>
      require(!orderExist(order.id), s"Duplicated OrderId: ${order.id}")
      ordersProperty.add(new OrderProperties(order))

    case OrderStatusChangedEvent(order, prevStatus, newStatus) =>
      withOrder(order) { o => o.updateStatus(newStatus) }

    case OrderProgressedEvent(order, _, newProgress) =>
      withOrder(order) { o => o.updateProgress(newProgress) }

    case WalletBalanceChangeEvent(newBalance) =>
      _walletBalanceProperty.set(Some(newBalance))

    case FiatBalanceChangeEvent(newBalance) if newBalance.amount.currency == Euro =>
      _fiatBalanceProperty.set(Some(newBalance.asInstanceOf[Balance[Euro.type]]))
  }

  private def orderExist(orderId: OrderId): Boolean =
    ordersProperty.exists(_.idProperty.value == orderId)

  private def withOrder(orderId: OrderId)(f: OrderProperties => Unit): Unit =
    ordersProperty.find(_.idProperty.value == orderId).foreach(f)

  app.observe(eventHandler)
}
