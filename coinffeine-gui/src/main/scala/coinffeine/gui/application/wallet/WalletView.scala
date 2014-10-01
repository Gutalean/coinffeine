package coinffeine.gui.application.wallet

import scalafx.Includes._
import scalafx.geometry.{HPos, Insets, Pos}
import scalafx.scene.Node
import scalafx.scene.control.TableColumn._
import scalafx.scene.control.{Button, Label, TableColumn, TableView}
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.scene.layout._

import com.google.bitcoin.core.Sha256Hash
import org.joda.time.DateTime

import coinffeine.gui.application.ApplicationView
import coinffeine.gui.application.properties.{WalletActivityEntryProperties, WalletProperties}
import coinffeine.gui.qrcode.QRCode
import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.bitcoin.Address
import coinffeine.model.currency.{Bitcoin, BitcoinBalance}
import coinffeine.peer.api.CoinffeineApp

class WalletView(app: CoinffeineApp, properties: WalletProperties) extends ApplicationView {

  override val name = "Wallet"

  private val balanceDetailsPane = new GridPane() {
    id = "wallet-balance-details"
    columnConstraints = Seq(
      new ColumnConstraints() { halignment = HPos.RIGHT },
      new ColumnConstraints() { halignment = HPos.LEFT }
    )

    val lines: Seq[(String, BitcoinBalance => String)] = Seq(
      "Active balance:" -> (_.estimated.toString),
      "Available balance:" -> (_.available.toString),
      "Funds in use:" -> (_.blocked.toString),
      "Minimum output:" -> (_.minOutput.map(_.toString).getOrElse("None"))
    )

    lines.zipWithIndex.foreach { case ((title, valueExtractor), index) =>
      add(new Label(title), 0, index)
      add(new Label() {
        text <== app.wallet.balance.map {
          case Some(balance) => valueExtractor(balance)
          case None => "unknown"
        }
      }, 1, index)
    }
  }

  private val leftDetailsPane = new VBox() {
    id = "wallet-left-pane"
    hgrow = Priority.ALWAYS
    content = Seq(
      new Label("Wallet funds") {
        styleClass = Seq("title")
      },
      balanceDetailsPane
    )
  }

  private def qrCodeImage(address: Address): Image = QRCode.encode(s"bitcoin:$address", 170)

  private val noQrCode: Node = new Label("No public address available") {
    id = "wallet-noqr-label"
  }
  private def qrCode(address: Address): Node = new VBox() {
    content = Seq(
      new ImageView(qrCodeImage(address)),
      new Label(address.toString) {
        id = "wallet-qr-label"
      }
    )
  }

  private val qrCodePane = new StackPane() {
    id = "wallet-qr-pane"
    app.wallet.primaryAddress.bindToList(content) {
      case Some(addr) => Seq(qrCode(addr))
      case None => Seq(noQrCode)
    }
  }

  private val copyToClipboardButton = new Button("Copy address to clipboard") {
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
    onAction = { action: Any =>
      val form = new WithdrawFundsForm(app.wallet)
      form.show() match {
        case WithdrawFundsForm.Withdraw(amount, to) =>
          app.wallet.transfer(amount, to)
        case _ =>
      }
    }
  }

  private val rightDetailsPane = new VBox() {
    id = "wallet-right-pane"
    content = Seq(qrCodePane, copyToClipboardButton, withdrawFundsButton)
  }

  private val detailsPane = new HBox() {
    id = "wallet-details-pane"
    content = Seq(leftDetailsPane, rightDetailsPane)
  }

  private val transactionsTable = new TableView[WalletActivityEntryProperties](properties.transactions) {
    id = "wallet-transactions-table"
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
      new TableColumn[WalletActivityEntryProperties, Bitcoin.Amount] {
        text = "Amount"
        cellValueFactory = { _.value.amount }
      }
    )
  }

  private val transactionsPane = new VBox() {
    id = "wallet-transactions-pane"
    content = Seq(
      new Label("Wallet transactions") { styleClass = Seq("title") },
      transactionsTable)
  }

  override val centerPane = new VBox() {
    id = "wallet-center-pane"
    content = Seq(detailsPane, transactionsPane)
  }
}
