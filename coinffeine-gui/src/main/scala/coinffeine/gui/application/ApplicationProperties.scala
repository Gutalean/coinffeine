package coinffeine.gui.application

import javafx.beans.value.{ChangeListener, ObservableValue}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

import org.joda.time.DateTime

import coinffeine.gui.application.properties.{PeerOrders, PropertyBindings, WalletProperties}
import coinffeine.gui.beans.PollingBean
import coinffeine.gui.control.ConnectionStatus
import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.currency.balance.FiatBalances
import coinffeine.model.util.Cached
import coinffeine.peer.api.CoinffeineApp

class ApplicationProperties(app: CoinffeineApp, executor: ExecutionContext)
    extends PropertyBindings {

  import ApplicationProperties._
  import coinffeine.gui.util.FxExecutor.asContext

  val now = PollingBean[DateTime](TimeComputingInterval)(Future.successful(DateTime.now()))

  val ordersProperty = new PeerOrders(app.operations, executor)

  val wallet = new WalletProperties(app.wallet)

  val fiatBalancesProperty: ReadOnlyObjectProperty[Cached[FiatBalances]] = {
    val property = new ObjectProperty(this, "balance", Cached.stale(FiatBalances.empty))
    app.paymentProcessor.balances.onNewValue(property.set)
    property
  }

  def connectionStatusProperty: ReadOnlyObjectProperty[ConnectionStatus] =
    _connectionStatus

  private val _connectionStatus = {
    val status = new ObjectProperty(this, "connectionStatus",
      ConnectionStatus(
        ConnectionStatus.Coinffeine(0, None),
        ConnectionStatus.Bitcoin(0, BlockchainStatus.NotDownloading(lastBlock = None)),
        DateTime.now()))
    now.addListener(new ChangeListener[Option[DateTime]] {
      override def changed(observable: ObservableValue[_ <: Option[DateTime]],
                           oldValue: Option[DateTime],
                           newValue: Option[DateTime]) = {
        status.value = status.value.copy(now = newValue.getOrElse(DateTime.now()))
      }
    })
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

object ApplicationProperties {
  val TimeComputingInterval = 10.seconds
}
