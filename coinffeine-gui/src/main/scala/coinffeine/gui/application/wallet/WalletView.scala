package coinffeine.gui.application.wallet

import scalafx.Includes._
import scalafx.event.Event
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.scene.layout._
import java.net.{URI, URL}
import java.util.Locale

import org.joda.time.format.DateTimeFormat

import coinffeine.gui.application.ApplicationView
import coinffeine.gui.application.properties.{WalletActivityEntryProperties, WalletProperties}
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, GlyphLabel}
import coinffeine.gui.pane.PagePane
import coinffeine.gui.qrcode.QRCode
import coinffeine.gui.scene.styles.{ButtonStyles, PaneStyles}
import coinffeine.gui.util.Browser
import coinffeine.model.bitcoin.Address
import coinffeine.peer.api.CoinffeineApp

class WalletView(app: CoinffeineApp, properties: WalletProperties) extends ApplicationView {

  override val name = "Wallet"

  private val fundsDialog = new FundsDialog(properties)

  private def iconFor(tx: WalletActivityEntryProperties): GlyphIcon =
    if (tx.amount.value.isNegative) GlyphIcon.BitcoinOutflow else GlyphIcon.BitcoinInflow

  private def actionFor(tx: WalletActivityEntryProperties): String =
    if (tx.amount.value.isNegative) "withdrawn from" else "added to"

  private def dateFor(tx: WalletActivityEntryProperties): String = {
    val date = tx.time.value
    WalletView.DateFormat.print(date)
  }

  private val transactionsTable = new VBox {
    styleClass += "transactions"
    properties.transactions.bindToList(content) { tx =>
      new HBox {
        styleClass += "line"
        content = Seq(
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
            content = Seq(
              new Button with ButtonStyles.Details {
                onAction = { e: Event =>
                  Browser.default.browse(WalletView.detailsOfTransaction(tx.hash.value.toString))
                }
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

  override def controlPane: Pane = new HBox {

    id = "wallet-control-pane"

    private val noPrimaryAddress = app.wallet.primaryAddress.mapToBoolean(_.isEmpty)
    private val noAvailableFunds = app.wallet.balance.mapToBoolean(!_.exists(_.available.isPositive))

    private val qrCodePane = new StackPane() {
      private def qrCodeImage(address: Address): Node =
        new ImageView(QRCode.encode(s"bitcoin:$address", 145))

      private val noQrCode: Node = new Label("No public address available")

      app.wallet.primaryAddress.bindToList(content) { address =>
        Seq(address.fold(noQrCode)(qrCodeImage))
      }
    }

    private val fundsButton = new Button("Wallet funds") {
      onAction = fundsDialog.show _
    }

    private val copyToClipboardButton = new Button("Copy address") {
      disable <== noPrimaryAddress
      onAction = () => {
        val content = new ClipboardContent()
        content.putString(app.wallet.primaryAddress.get.get.toString)
        Clipboard.systemClipboard.setContent(content)
      }
    }

    private val sendButton = new Button("Send") {
      disable <== noPrimaryAddress || noAvailableFunds
      onAction = () => {
        new SendFundsForm(app.wallet).show() match {
          case SendFundsForm.Send(amount, to) => app.wallet.transfer(amount, to)
          case SendFundsForm.CancelSend => // Do nothing
        }
      }
    }

    private val buttons = new VBox with PaneStyles.ButtonColumn {
      content = Seq(fundsButton, copyToClipboardButton, sendButton)
    }

    content = Seq(qrCodePane, buttons)
  }
}

object WalletView {

  private val TransactionInfoBaseUri = new URL("http://testnet.trial.coinffeine.com/tx/")

  private val DateFormat = DateTimeFormat
    .forPattern("ddMMMyyyy hh:mm:ss")
    .withLocale(Locale.US)

  private def detailsOfTransaction(txHash: String): URI =
    new URL(TransactionInfoBaseUri, txHash).toURI
}
