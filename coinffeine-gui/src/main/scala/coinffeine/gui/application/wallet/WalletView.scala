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
import coinffeine.model.bitcoin.WalletActivity.EntryType
import coinffeine.model.bitcoin.{Hash, WalletActivity}
import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.peer.api.CoinffeineWallet
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.bitcoin.BitcoinSettings.{IntegrationRegnet, MainNet, PublicTestnet}

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
        icon <== activity.entry.delegate.map(entry => iconFor(entry.entryType))
      },
      new Label {
        styleClass += "summary"
        text <== activity.entry.delegate.mapToString(summarize)
      },
      new Label {
        styleClass += "date"
        text <== activity.time.delegate.mapToString(WalletView.DateFormat.print)
      },
      new HBox {
        styleClass += "buttons"
        children = Seq(
          new Button with ButtonStyles.Details {
            onAction = () =>
              Browser.default.browse(detailsOfTransaction(activity.hash.value))
          }
        )
      }
    )

    private def summarize(entry: WalletActivity.Entry): String =
      s"${entry.amount.abs} ${descriptionFor(entry.entryType)}"
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

  private def iconFor(entryType: EntryType) = entryType match {
    case EntryType.DepositLock(_) | EntryType.DepositUnlock(_) => GlyphIcon.ExchangeTypes
    case EntryType.OutFlow => GlyphIcon.BitcoinOutflow
    case EntryType.InFlow => GlyphIcon.BitcoinInflow
  }

  private def descriptionFor(entryType: EntryType) = entryType match {
    case EntryType.DepositLock(_) => "locked for an exchange"
    case EntryType.DepositUnlock(_) => "unlocked from an exchange"
    case EntryType.OutFlow => "withdrawn from your wallet"
    case EntryType.InFlow => "added to your wallet"
  }
}

object WalletView {

  private val TransactionDetailsURLFormats = Map[BitcoinSettings.Network, String](
    PublicTestnet -> "http://testnet.trial.coinffeine.com/tx/%s",
    IntegrationRegnet -> "http://testnet.test.coinffeine.com/tx/%s",
    MainNet -> "https://blockchain.info/tx/%s"
  )

  private val DateFormat = DateTimeFormat
    .forPattern("ddMMMyyyy HH:mm:ss")
    .withLocale(Locale.US)
}
