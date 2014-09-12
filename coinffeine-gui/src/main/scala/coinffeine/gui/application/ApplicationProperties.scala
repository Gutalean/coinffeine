package coinffeine.gui.application

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.collections.ObservableBuffer

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.control.CombinedConnectionStatus
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
  def connectionStatusProperty: ReadOnlyObjectProperty[CombinedConnectionStatus] =
    _connectionStatus

  private val _fiatBalanceProperty =
    new ObjectProperty[Option[FiatBalance]](this, "fiatBalance", None)

  private val _connectionStatus = {
    val status = new ObjectProperty(this, "connectionStatus",
      CombinedConnectionStatus(CoinffeineConnectionStatus(0, None),
        BitcoinConnectionStatus(0, BlockchainStatus.NotDownloading)))
    app.bitcoinNetwork.activePeers.onChange { case (_, newValue) =>
      val bitcoinStatus = status.value.bitcoinStatus
      status.value = status.value.copy(
        bitcoinStatus = bitcoinStatus.copy(activePeers = newValue))
    }
    app.bitcoinNetwork.blockchainStatus.onChange { case (_, newValue) =>
      val bitcoinStatus = status.value.bitcoinStatus
      status.value = status.value.copy(
        bitcoinStatus = bitcoinStatus.copy(blockchainStatus = newValue))
    }
    app.network.activePeers.onChange { case (_, newValue) =>
      val coinffeineStatus = status.value.coinffeineStatus
      status.value = status.value.copy(
        coinffeineStatus = coinffeineStatus.copy(activePeers = newValue))
    }
    app.network.brokerId.onChange { case (_, newValue) =>
      val coinffeineStatus = status.value.coinffeineStatus
      status.value = status.value.copy(
        coinffeineStatus = coinffeineStatus.copy(brokerId = newValue))
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

    case status: BitcoinConnectionStatus =>
      _connectionStatus.set(_connectionStatus.value.copy(bitcoinStatus = status))

    case status: CoinffeineConnectionStatus =>
      _connectionStatus.set(_connectionStatus.value.copy(coinffeineStatus = status))
  }

  private def orderExist(orderId: OrderId): Boolean =
    ordersProperty.exists(_.idProperty.value == orderId)

  private def withOrder(orderId: OrderId)(f: OrderProperties => Unit): Unit =
    ordersProperty.find(_.idProperty.value == orderId).foreach(f)

  private def bindTo[A](prop: Property[A], name: String): ReadOnlyObjectProperty[A] = {
    val result = new ObjectProperty[A](this, name, prop.get)
    prop.onChange { case (_, newValue) => result.value = newValue }
    result
  }

  app.observe(eventHandler)
}
