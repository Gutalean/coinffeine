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
import coinffeine.gui.control.{GlyphIcon, GlyphLabel}
import coinffeine.gui.pane.PagePane
import coinffeine.gui.scene.styles.ButtonStyles
import coinffeine.gui.util.Browser
import coinffeine.peer.api.CoinffeineWallet
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.bitcoin.BitcoinSettings.{IntegrationRegnet, MainNet, PublicTestnet}

class WalletView(
    network: BitcoinSettings.Network,
    wallet: CoinffeineWallet,
    properties: WalletProperties) extends ApplicationView {

  override val name = "Wallet"

  private def iconFor(tx: WalletActivityEntryProperties): GlyphIcon =
    if (tx.amount.value.isNegative) GlyphIcon.BitcoinOutflow else GlyphIcon.BitcoinInflow

  private def actionFor(tx: WalletActivityEntryProperties): String =
    if (tx.amount.value.isNegative) "withdrawn from" else "added to"

  private def dateFor(tx: WalletActivityEntryProperties): String =
    WalletView.DateFormat.print(tx.time.value)

  private val transactionsTable = new VBox {
    styleClass += "transactions"
    val sortedList = new ObservableBuffer(new SortedList[WalletActivityEntryProperties](
      properties.transactions.delegate, new TransactionTimestampComparator))
    sortedList.bindToList(children) { tx =>
      new HBox {
        styleClass += "line"
        children = Seq(
          new GlyphLabel {
            styleClass += "icon"
            icon = iconFor(tx)
          },
          new Label(s"${tx.amount.value} ${actionFor(tx)} your wallet") {
            styleClass += "summary"
          },
          new Label(dateFor(tx)) { styleClass += "date" },
          new HBox {
            styleClass += "buttons"
            children = Seq(
              new Button with ButtonStyles.Details {
                onAction = () =>
                  Browser.default.browse(detailsOfTransaction(tx.hash.value.toString))
              }
            )
          }
        )
      }.delegate
    }
  }

  override val centerPane = new PagePane {
    id = "wallet-center-pane"
    headerText = "WALLET TRANSACTIONS"
    pageContent = transactionsTable
  }

  override def controlPane = new WalletControlPane(wallet, properties)

  private def detailsOfTransaction(txHash: String): URI =
    new URL(WalletView.TransactionDetailsURLFormat(network).format(txHash)).toURI
}

object WalletView {

  private val TransactionDetailsURLFormat = Map[BitcoinSettings.Network, String](
    PublicTestnet -> "http://testnet.trial.coinffeine.com/tx/%s",
    IntegrationRegnet -> "http://testnet.test.coinffeine.com/tx/%s",
    MainNet -> "https://blockchain.info/tx/%s"
  )

  private val DateFormat = DateTimeFormat
    .forPattern("ddMMMyyyy HH:mm:ss")
    .withLocale(Locale.US)
}
