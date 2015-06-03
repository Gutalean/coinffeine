package coinffeine.gui.setup

import scalafx.scene.control.{Label, TextField}
import scalafx.scene.image.ImageView
import scalafx.scene.layout.{Priority, HBox, VBox}

import coinffeine.gui.control.{GlyphIcon, SupportWidget}
import coinffeine.gui.qrcode.QRCode
import coinffeine.gui.wizard.StepPane

class TopUpStepPane(address: String) extends StepPane[SetupConfig] {
  override val icon = GlyphIcon.Number4

  private val title = new Label("Add bitcoins to your Coinffeine wallet") {
    styleClass += "title"
    hgrow = Priority.Always
  }

  private val subTitle = new HBox {
    styleClass += "subtitle"
    children = Seq(
      new Label("You need a small amount of bitcoins to buy bitcoins."),
      new SupportWidget("setup-topup")
    )
  }

  private val dataPane = new VBox {
    styleClass += "data"
    children = Seq(
      new Label("Your wallet address"),
      new TextField {
        id = "address"
        text = address
        editable = false
      },
      new ImageView(QRCode.encode(s"bitcoin:$address", 150))
    )
  }

  children = new VBox() {
    styleClass += "topup-pane"
    children = Seq(title, subTitle, dataPane)
  }
}
