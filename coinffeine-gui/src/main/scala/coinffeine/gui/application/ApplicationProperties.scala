package coinffeine.gui.application

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.collections.ObservableBuffer

import coinffeine.gui.application.properties.{WalletProperties, PropertyBindings, OrderProperties}
import coinffeine.gui.control.ConnectionStatus
import coinffeine.gui.util.FxEventHandler
import coinffeine.model.bitcoin.{BlockchainStatus, ImmutableTransaction}
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.{Balance, BitcoinBalance, Currency}
import coinffeine.model.event._
import coinffeine.model.market.OrderId
import coinffeine.peer.api.{CoinffeineApp, EventHandler}

class ApplicationProperties(app: CoinffeineApp) extends PropertyBindings {

  type FiatBalance = Balance[Euro.type]

  import coinffeine.gui.util.FxExecutor.asContext

  val ordersProperty = ObservableBuffer[OrderProperties]()

  val wallet = new WalletProperties(app.wallet)

  val fiatBalanceProperty: ReadOnlyObjectProperty[Option[FiatBalance]] =
    createBoundedToMapEntry(app.paymentProcessor.balance, "balance", Currency.Euro) {
      _.asInstanceOf[FiatBalance]
    }

  def connectionStatusProperty: ReadOnlyObjectProperty[ConnectionStatus] =
    _connectionStatus

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
  }

  private def orderExist(orderId: OrderId): Boolean =
    ordersProperty.exists(_.idProperty.value == orderId)

  private def withOrder(orderId: OrderId)(f: OrderProperties => Unit): Unit =
    ordersProperty.find(_.idProperty.value == orderId).foreach(f)

  app.observe(eventHandler)
}
