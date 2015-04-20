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
import coinffeine.gui.control.GlyphLabel
import coinffeine.gui.control.GlyphLabel.Icon
import coinffeine.gui.pane.PagePane
import coinffeine.gui.qrcode.QRCode
import coinffeine.gui.scene.styles.{ButtonStyles, PaneStyles}
import coinffeine.gui.util.Browser
import coinffeine.model.bitcoin.Address
import coinffeine.peer.api.CoinffeineApp

class WalletView(app: CoinffeineApp, properties: WalletProperties) extends ApplicationView {

  override val name = "Wallet"

  private def iconFor(tx: WalletActivityEntryProperties): Icon =
    if (tx.amount.value.isNegative) Icon.BitcoinOutflow else Icon.BitcoinInflow

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

    private def qrCodeImage(address: Address): Image = QRCode.encode(s"bitcoin:$address", 125)

    private val noQrCode: Node = new Label("No public address available")

    private val qrCodePane = new StackPane() {
      app.wallet.primaryAddress.bindToList(content) {
        case Some(addr) => Seq(new ImageView(qrCodeImage(addr)))
        case None => Seq(noQrCode)
      }
    }

    private val copyToClipboardButton = new Button("Copy address") {
      disable <== app.wallet.primaryAddress.mapToBoolean(_.isEmpty)
      onAction = { action: Any =>
        val content = new ClipboardContent()
        content.putString(app.wallet.primaryAddress.get.get.toString)
        Clipboard.systemClipboard.setContent(content)
      }
    }

    private val withdrawFundsButton = new Button("Withdraw") {
      disable <== app.wallet.primaryAddress.mapToBoolean(_.isEmpty) ||
        app.wallet.balance.mapToBoolean {
          case Some(balance) if balance.amount.isPositive => false
          case _ => true
        }
      onAction = { action: Any =>
        val form = new WithdrawFundsForm(app.wallet)
        form.show() match {
          case WithdrawFundsForm.Withdraw(amount, to) =>
            app.wallet.transfer(amount, to)
          case _ =>
        }
      }
    }

    private val buttons = new VBox with PaneStyles.ButtonColumn {
      content = Seq(copyToClipboardButton, withdrawFundsButton)
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
