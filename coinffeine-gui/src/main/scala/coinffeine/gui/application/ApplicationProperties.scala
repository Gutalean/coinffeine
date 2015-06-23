package coinffeine.gui.application

import scala.concurrent.ExecutionContext
import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

import coinffeine.gui.application.properties.{PeerOrders, PropertyBindings, WalletProperties}
import coinffeine.gui.control.ConnectionStatus
import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.currency.{Euro, FiatBalance}
import coinffeine.peer.api.CoinffeineApp

class ApplicationProperties(app: CoinffeineApp, executor: ExecutionContext) extends PropertyBindings {

  type EuroBalance = FiatBalance[Euro.type]

  import coinffeine.gui.util.FxExecutor.asContext

  val ordersProperty = new PeerOrders(app.operations, executor)

  val wallet = new WalletProperties(app.wallet)

  val fiatBalanceProperty: ReadOnlyObjectProperty[Option[EuroBalance]] =
    createBoundedToMapEntry(app.paymentProcessor.balance, "balance", Euro) {
      _.asInstanceOf[EuroBalance]
    }

  def connectionStatusProperty: ReadOnlyObjectProperty[ConnectionStatus] =
    _connectionStatus

  private val _connectionStatus = {
    val status = new ObjectProperty(this, "connectionStatus",
      ConnectionStatus(
        ConnectionStatus.Coinffeine(0, None),
        ConnectionStatus.Bitcoin(0, BlockchainStatus.NotDownloading(lastBlock = None))))
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
}
