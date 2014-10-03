package coinffeine.gui.setup

import java.net.URI
import scalafx.beans.property.ObjectProperty
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Hyperlink, Label, TextField}
import scalafx.scene.image.ImageView
import scalafx.scene.layout.{HBox, Priority, StackPane, VBox}
import scalafx.scene.text.Font

import coinffeine.gui.qrcode.QRCode
import coinffeine.gui.wizard.StepPane

private[setup] class TopUpStepPane(address: String) extends StackPane with StepPane[SetupConfig] {

  content = new VBox() {
    styleClass += "wizard-base-pane"
    content = Seq(
      new Label("Add bitcoins to your Coinffeine wallet") { styleClass = Seq("stepTitle") },
      new HBox {
        id = "wizard-topup-disclaimer-pane"
        content = Seq(
          new Label("You need a small amount of bitcoins to buy bitcoins."),
          new Hyperlink("Know why") {
            handleEvent(ActionEvent.ACTION) { () => openFAQ() }
          }
        )
      },
      new HBox() {
        id = "wizard-topup-walletinfo-pane"
        content = Seq(
          new VBox() {
            id = "wizard-topup-address-pane"
            hgrow = Priority.ALWAYS
            content = Seq(
              new Label("Your wallet address"),
              new TextField {
                id = "address"
                text = address
                editable = false
              }
            )
          },
          new ImageView(QRCode.encode(s"bitcoin:$address", 150))
        )
      }
    )
  }

  override def bindTo(data: ObjectProperty[SetupConfig]): Unit = {
    canContinue.value = true
  }

  private def openFAQ(): Unit = {
    java.awt.Desktop.getDesktop.browse(TopUpStepPane.FaqUrl)
  }
}

private[setup] object TopUpStepPane {
  val FaqUrl = new URI("http://www.coinffeine.com/faq.html")
}
