package coinffeine.gui.application.wallet

import java.net.{URI, URL}
import scala.util.Try
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.HPos
import scalafx.scene.Node
import scalafx.scene.control.TableColumn._
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.scene.layout._

import org.joda.time.format.DateTimeFormat

import coinffeine.gui.application.ApplicationView
import coinffeine.gui.application.properties.{WalletActivityEntryProperties, WalletProperties}
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.qrcode.QRCode
import coinffeine.gui.scene.styles.PaneStyles
import coinffeine.gui.util.Browser
import coinffeine.model.bitcoin.{Address, Hash}
import coinffeine.model.currency.{Bitcoin, BitcoinBalance}
import coinffeine.peer.api.CoinffeineApp

class WalletView(app: CoinffeineApp, properties: WalletProperties) extends ApplicationView {

  override val name = "Wallet"

  private val balanceDetailsPane = new GridPane() {
    id = "wallet-balance-details"
    columnConstraints = Seq(
      new ColumnConstraints() { halignment = HPos.Right},
      new ColumnConstraints() { halignment = HPos.Left }
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
    hgrow = Priority.Always
    content = Seq(
      new Label("Wallet funds") {
        styleClass = Seq("title")
      },
      balanceDetailsPane
    )
  }

  private val rightDetailsPane = new VBox() {
    id = "wallet-right-pane"
  }

  private val detailsPane = new HBox() {
    id = "wallet-details-pane"
    content = Seq(leftDetailsPane, rightDetailsPane)
  }

  private val transactionsTable = new TableView[WalletActivityEntryProperties](properties.transactions) {
    id = "wallet-transactions-table"
    placeholder = new Label("No transactions found")
    hgrow = Priority.Always
    columns ++= Seq(
      new TableColumn[WalletActivityEntryProperties, String] {
        text = "Time"
        cellValueFactory = {
          _.value.time.delegate.mapToString { instant =>
            DateTimeFormat.mediumDateTime().print(instant.toLocalDateTime)
          }
        }
      },
      new TableColumn[WalletActivityEntryProperties, Hash] {
        text = "Hash"
        cellValueFactory = { _.value.hash }
        cellFactory = { _ => new TableCell[WalletActivityEntryProperties, Hash] {
          item.onChange { (_, _, hash) =>
            val hashString = Try(hash.toString).getOrElse("---")
            graphic = new HBox(spacing = 5) {
              styleClass += "hash-cell"
              content = Seq(
                new Label(hashString) {
                  maxWidth = Double.MaxValue
                  hgrow = Priority.Always
                },
                new Button("More") {
                  onAction = { _: ActionEvent =>
                    Browser.default.browse(WalletView.detailsOfTransaction(hashString))
                  }
                }
              )
            }
          }
        }}
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

    private val buttons = new VBox with PaneStyles.ButtonColumn {
      content = Seq(copyToClipboardButton, withdrawFundsButton)
    }

    content = Seq(qrCodePane, buttons)
  }
}

object WalletView {

  private val TransactionInfoBaseUri = new URL("http://testnet.trial.coinffeine.com/tx/")

  private def detailsOfTransaction(txHash: String): URI =
    new URL(TransactionInfoBaseUri, txHash).toURI
}
