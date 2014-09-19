package coinffeine.gui.application.wallet

import java.util.Date
import scalafx.Includes._
import scalafx.geometry.{HPos, Insets, Pos}
import scalafx.scene.Node
import scalafx.scene.control.{TableColumn, TableView, Button, Label}
import scalafx.scene.control.TableColumn._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.scene.layout._

import com.google.bitcoin.core.Sha256Hash
import org.joda.time.DateTime

import coinffeine.gui.application.ApplicationView
import coinffeine.gui.application.properties.{WalletProperties, WalletActivityEntryProperties}
import coinffeine.gui.qrcode.QRCode
import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.bitcoin.Address
import coinffeine.model.currency.BitcoinAmount
import coinffeine.peer.api.CoinffeineApp

class WalletView(app: CoinffeineApp, properties: WalletProperties) extends ApplicationView {

  override val name = "Wallet"

  private val balanceDetailsPane = new GridPane() {
    hgap = 10
    columnConstraints = Seq(
      new ColumnConstraints() { halignment = HPos.RIGHT },
      new ColumnConstraints() { halignment = HPos.LEFT }
    )
    add(new Label("Total balance: "), 0, 0)
    add(new Label() {
      text <== app.wallet.balance.map {
        case Some(balance) => balance.amount.toString
        case None => "unknown"
      }
    }, 1, 0)
  }

  private val leftDetailsPane = new VBox(spacing = 5) {
    hgrow = Priority.ALWAYS
    margin = Insets(20)
    content = Seq(
      new Label("Wallet funds") {
        styleClass = Seq("title")
      },
      balanceDetailsPane
    )
  }

  private def qrCodeImage(address: Address): Image = QRCode.encode(s"bitcoin:$address", 170)

  private val noQrCode: Node = new Label("No public address available") {
    alignment = Pos.CENTER
    minHeight = 170
    minWidth = 170
  }
  private def qrCode(address: Address): Node = new VBox() {
    content = Seq(
      new ImageView(qrCodeImage(address)),
      new Label(address.toString) {
        alignment = Pos.TOP_CENTER
        margin = Insets(0, 0, 10, 0)
      }
    )
  }

  private val qrCodePane = new StackPane() {
    id = "qr-pane"
    padding = Insets(5)
    alignment = Pos.TOP_RIGHT
    minWidth = 180
    maxWidth = 180
    minHeight = 190
    maxHeight = 190

    app.wallet.primaryAddress.bindToList(content) {
      case Some(addr) => Seq(qrCode(addr))
      case None => Seq(noQrCode)
    }
  }

  private val copyToClipboardButton = new Button("Copy address to clipboard") {
    maxWidth = Double.MaxValue

    disable <== app.wallet.primaryAddress.mapToBoolean(!_.isDefined)
    onAction = { action: Any =>
      val content = new ClipboardContent()
      content.putString(app.wallet.primaryAddress.get.get.toString)
      Clipboard.systemClipboard.setContent(content)
    }
  }

  private val withdrawFundsButton = new Button("Withdraw") {
    disable <== app.wallet.primaryAddress.mapToBoolean(!_.isDefined) ||
      app.wallet.balance.mapToBoolean {
        case Some(balance) if balance.amount.isPositive => false
        case _ => true
      }
    maxWidth = Double.MaxValue
    onAction = { action: Any =>
      val form = new WithdrawFundsForm(app.wallet)
      form.show() match {
        case WithdrawFundsForm.Withdraw(amount, to) =>
          app.wallet.transfer(amount, to)
        case _ =>
      }
    }
  }

  private val rightDetailsPane = new VBox(spacing = 5) {
    padding = Insets(10)
    alignment = Pos.TOP_CENTER
    content = Seq(qrCodePane, copyToClipboardButton, withdrawFundsButton)
  }

  private val detailsPane = new HBox(spacing = 5) {
    content = Seq(leftDetailsPane, rightDetailsPane)
  }

  private val transactionsTable = new TableView[WalletActivityEntryProperties](properties.transactions) {
    placeholder = new Label("No transactions found")
    hgrow = Priority.ALWAYS
    columns ++= Seq(
      new TableColumn[WalletActivityEntryProperties, DateTime] {
        text = "Time"
        cellValueFactory = { _.value.time }
      },
      new TableColumn[WalletActivityEntryProperties, Sha256Hash] {
        text = "Hash"
        cellValueFactory = { _.value.hash }
      },
      new TableColumn[WalletActivityEntryProperties, BitcoinAmount] {
        text = "Amount"
        cellValueFactory = { _.value.amount }
      }
    )
  }

  private val transactionsPane = new VBox(spacing = 10) {
    padding = Insets(10)
    content = Seq(
      new Label("Wallet transactions") { styleClass = Seq("title") },
      transactionsTable)
  }

  override val centerPane = new VBox(spacing = 10) {
    content = Seq(detailsPane, transactionsPane)
  }
}
