package coinffeine.gui.application

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.collections.ObservableBuffer

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.control.ConnectionStatus
import coinffeine.gui.util.FxEventHandler
import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.currency.{BitcoinBalance, Balance}
import coinffeine.model.currency.Currency.{Bitcoin, Euro}
import coinffeine.model.event._
import coinffeine.model.market.OrderId
import coinffeine.model.properties.Property
import coinffeine.peer.api.{CoinffeineApp, EventHandler}

class ApplicationProperties(app: CoinffeineApp) {

  type FiatBalance = Balance[Euro.type]

  import coinffeine.gui.util.FxExecutor.asContext

  val ordersProperty = ObservableBuffer[OrderProperties]()

  val walletBalanceProperty: ReadOnlyObjectProperty[Option[BitcoinBalance]] =
    bindTo(app.wallet.balance, "WalletBalance")
  def fiatBalanceProperty: ReadOnlyObjectProperty[Option[FiatBalance]] = _fiatBalanceProperty
  def connectionStatusProperty: ReadOnlyObjectProperty[ConnectionStatus] =
    _connectionStatus

  private val _fiatBalanceProperty =
    new ObjectProperty[Option[FiatBalance]](this, "fiatBalance", None)

  private val _connectionStatus = {
    val status = new ObjectProperty(this, "connectionStatus",
      ConnectionStatus(
        ConnectionStatus.Coinffeine(0, None),
        ConnectionStatus.Bitcoin(0, BlockchainStatus.NotDownloading)))
    app.bitcoinNetwork.activePeers.onNewValue { newValue =>
      val bitcoinStatus = status.value.bitcoin
      status.value = status.value.copy(
        bitcoin = bitcoinStatus.copy(activePeers = newValue))
    }
    app.bitcoinNetwork.blockchainStatus.onNewValue { newValue =>
      val bitcoinStatus = status.value.bitcoin
      status.value = status.value.copy(
        bitcoin = bitcoinStatus.copy(blockchainStatus = newValue))
    }
    app.network.activePeers.onNewValue { newValue =>
      val coinffeineStatus = status.value.coinffeine
      status.value = status.value.copy(
        coinffeine = coinffeineStatus.copy(activePeers = newValue))
    }
    app.network.brokerId.onNewValue { newValue =>
      val coinffeineStatus = status.value.coinffeine
      status.value = status.value.copy(
        coinffeine = coinffeineStatus.copy(brokerId = newValue))
    }
    status
  }

  private val eventHandler: EventHandler = FxEventHandler {

    case OrderSubmittedEvent(order) =>
      require(!orderExist(order.id), s"Duplicated OrderId: ${order.id}")
      ordersProperty.add(new OrderProperties(order))

    case OrderStatusChangedEvent(order, prevStatus, newStatus) if prevStatus != newStatus =>
      withOrder(order) { o => o.updateStatus(newStatus) }

    case OrderProgressedEvent(order, _, newProgress) =>
      withOrder(order) { o => o.updateProgress(newProgress) }

    case FiatBalanceChangeEvent(newBalance) if newBalance.amount.currency == Euro =>
      _fiatBalanceProperty.set(Some(newBalance.asInstanceOf[Balance[Euro.type]]))
  }

  private def orderExist(orderId: OrderId): Boolean =
    ordersProperty.exists(_.idProperty.value == orderId)

  private def withOrder(orderId: OrderId)(f: OrderProperties => Unit): Unit =
    ordersProperty.find(_.idProperty.value == orderId).foreach(f)

  private def bindTo[A](prop: Property[A], name: String): ReadOnlyObjectProperty[A] = {
    val result = new ObjectProperty[A](this, name, prop.get)
    prop.onNewValue { newValue => result.value = newValue }
    result
  }

  app.observe(eventHandler)
}
