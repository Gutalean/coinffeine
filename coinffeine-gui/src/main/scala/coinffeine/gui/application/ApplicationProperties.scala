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

  def walletBalanceProperty: ReadOnlyObjectProperty[BitcoinAmount] = _walletBalanceProperty

  def fiatBalanceProperty: ReadOnlyObjectProperty[CurrencyAmount[Euro.type]] = _fiatBalanceProperty

  private val _walletBalanceProperty = new ObjectProperty[BitcoinAmount](
    this, "walletBalance", app.wallet.currentBalance())

  private val _fiatBalanceProperty = new ObjectProperty[CurrencyAmount[Euro.type]](
    this, "fiatBalance", app.paymentProcessor.currentBalance())

  private val eventHandler: EventHandler = FxEventHandler {

    case OrderSubmittedEvent(order) =>
      require(!orderExist(order.id), s"Duplicated OrderId: ${order.id}")
      ordersProperty.add(OrderProperties(order))

    case OrderCancelledEvent(orderId) =>
      ordersProperty.zipWithIndex.foreach { t =>
        if(t._1.order.id == orderId) {
          ordersProperty.remove(t._2)
        }
      }

    case WalletBalanceChangeEvent(newBalance) =>
      _walletBalanceProperty.set(newBalance)

    case FiatBalanceChangeEvent(newBalance @ CurrencyAmount(_, Euro)) =>
      _fiatBalanceProperty.set(newBalance.asInstanceOf[CurrencyAmount[Euro.type]])
  }

  private def orderExist(orderId: OrderId): Boolean = ordersProperty.exists(_.order.id == orderId)

  app.observe(eventHandler)
}
