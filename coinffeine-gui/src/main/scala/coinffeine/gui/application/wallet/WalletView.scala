package coinffeine.gui.application.wallet

import java.net.{URI, URL}
import java.util.Locale
import javafx.collections.transformation.SortedList
import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.scene.control._
import scalafx.scene.layout._

import org.joda.time.format.DateTimeFormat

import coinffeine.gui.application.ApplicationView
import coinffeine.gui.application.properties.{WalletActivityEntryProperties, WalletProperties}
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.GlyphLabel
import coinffeine.gui.pane.PagePane
import coinffeine.gui.scene.styles.ButtonStyles
import coinffeine.gui.util.Browser
import coinffeine.model.bitcoin.{BitcoinFeeCalculator, Hash}
import coinffeine.peer.api.CoinffeineWallet
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.bitcoin.BitcoinSettings.{IntegrationRegnet, MainNet}

class WalletView(
    network: BitcoinSettings.Network,
    wallet: CoinffeineWallet,
    feeCalculator: BitcoinFeeCalculator,
    properties: WalletProperties) extends ApplicationView {

  import WalletView._

  override val name = "Wallet"

  private class WalletActivityRow(activity: WalletActivityEntryProperties) extends HBox {
    styleClass += "line"
    children = Seq(
      new GlyphLabel {
        styleClass += "icon"
        icon <== activity.view.delegate.map(_.icon)
      },
      new Label {
        styleClass += "summary"
        text <== activity.view.delegate.mapToString(_.summary)
      },
      new Label {
        styleClass += "date"
        text <== activity.view.delegate.mapToString(v => WalletView.DateFormat.print(v.timestamp))
      },
      new HBox {
        styleClass += "buttons"
        children = Seq(
          new Button with ButtonStyles.Details {
            onAction = () =>
              Browser.default.browse(detailsOfTransaction(activity.view.value.hash))
          }
        )
      }
    )
  }

  private val walletActivity = new VBox {
    styleClass += "transactions"
    val sortedList = new ObservableBuffer(new SortedList[WalletActivityEntryProperties](
      properties.activities.delegate, new TransactionTimestampComparator))
    sortedList.bindToList(children) { activity => new WalletActivityRow(activity).delegate }
  }

  override val centerPane = new PagePane {
    id = "wallet-center-pane"
    headerText = "WALLET ACTIVITY"
    pageContent = walletActivity
  }

  override def controlPane = new WalletControlPane(wallet, feeCalculator, properties)

  private def detailsOfTransaction(tx: Hash): URI =
    new URL(TransactionDetailsURLFormats(network).format(tx)).toURI
}

object WalletView {

  private val TransactionDetailsURLFormats = Map[BitcoinSettings.Network, String](
    IntegrationRegnet -> "http://testnet.test.coinffeine.com/tx/%s",
    MainNet -> "https://blockchain.info/tx/%s"
  )

  private val DateFormat = DateTimeFormat
    .forPattern("ddMMMyyyy HH:mm:ss")
    .withLocale(Locale.US)
}
