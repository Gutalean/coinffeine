package coinffeine.gui.control

import scalafx.scene.control.Label
import scalafx.scene.layout.{HBox, VBox}

import coinffeine.gui.scene.styles.NodeStyles

class PaymentProcessorWidget extends HBox with NodeStyles.HExpand {
  id = "payment-processor"
  content = Seq(
    new VBox {
      styleClass += "legend"
      content = Seq(
        new Label("PAYMENT PROCESSOR"),
        new Label("OKPAY") {
          styleClass += "name"
        }
      )
    },
    new GlyphLabel { icon = GlyphIcon.OkPay }
  )
}
