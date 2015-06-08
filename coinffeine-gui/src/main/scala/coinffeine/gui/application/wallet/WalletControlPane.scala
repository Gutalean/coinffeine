package coinffeine.gui.application.wallet

import scalafx.Includes._
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.image.ImageView
import scalafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.scene.layout._

import coinffeine.gui.application.properties.WalletProperties
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.qrcode.QRCode
import coinffeine.gui.scene.styles.PaneStyles
import coinffeine.model.bitcoin.{BitcoinFeeCalculator, Address}
import coinffeine.peer.api.CoinffeineWallet

class WalletControlPane(wallet: CoinffeineWallet,
                        feeCalculator: BitcoinFeeCalculator,
                        properties: WalletProperties) extends HBox {
  id = "wallet-control-pane"

  private val fundsDialog = new FundsDialog(properties)
  private val noPrimaryAddress = wallet.primaryAddress.mapToBoolean(_.isEmpty)
  private val noAvailableFunds = wallet.balance.mapToBoolean(!_.exists(_.available.isPositive))

  private val qrCodePane = new StackPane() {
    private def qrCodeImage(address: Address): Node =
      new ImageView(QRCode.encode(s"bitcoin:$address", 120, 5))

    private val noQrCode: Node = new Label("No public address available")

    wallet.primaryAddress.bindToList(children) { address =>
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
      content.putString(wallet.primaryAddress.get.get.toString)
      Clipboard.systemClipboard.setContent(content)
    }
  }

  private val sendButton = new Button("Send") {
    disable <== noPrimaryAddress || noAvailableFunds
    onAction = () => {
      new SendFundsForm(wallet, feeCalculator).show() match {
        case SendFundsForm.Send(amount, to) => wallet.transfer(amount, to)
        case SendFundsForm.CancelSend => // Do nothing
      }
    }
  }

  private val buttons =
    new VBox(fundsButton, copyToClipboardButton, sendButton) with PaneStyles.ButtonColumn

  children = Seq(qrCodePane, buttons)
}
