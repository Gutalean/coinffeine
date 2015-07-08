package coinffeine.gui.control

import scalafx.Includes._
import scalafx.scene.control.Label
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{HBox, VBox}

import coinffeine.gui.preferences.PaymentProcessorSettingsDialog
import coinffeine.gui.scene.styles.NodeStyles

class PaymentProcessorWidget(settingsForm: PaymentProcessorSettingsDialog)
  extends HBox with NodeStyles.HExpand {

  id = "payment-processor"
  children = Seq(
    new VBox {
      styleClass += "legend"
      children = Seq(
        new Label("PAYMENT PROCESSOR"),
        new Label("OKPAY") {
          styleClass += "name"
          onMouseClicked = (ev: MouseEvent) => settingsForm.show()
        }
      )
    },
    new GlyphLabel {
      icon = GlyphIcon.OkPay
      onMouseClicked = (ev: MouseEvent) => settingsForm.show()
    }
  )
}
