package coinffeine.gui.setup

import scalafx.scene.control._
import scalafx.scene.layout._

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{SupportWidget, GlyphIcon}
import coinffeine.gui.wizard.StepPane
import coinffeine.peer.payment.okpay.{OkPaySettings, OkPayWalletAccess}

private[setup] class OkPayWalletDataPane(
    data: SetupConfig) extends StackPane with StepPane[SetupConfig] {

  override val icon = GlyphIcon.Number3

  private val title = new Label("Configure your OKPay account") { styleClass += "title" }

  private val subtitle = new HBox {
    styleClass += "subtitle"
    content = Seq(
      new Label("Please insert your OKPay API information"),
      new SupportWidget("setup-credentials")
    )
  }

  private val accountIdLabel = new Label("Your account ID")

  private val accountIdField = new TextField {}

  private val tokenLabel = new Label("Your token")

  private val tokenField = new TextField {}

  private val dataPane = new VBox {
    styleClass += "data"
    content = Seq(accountIdLabel, accountIdField, tokenLabel, tokenField)
  }

  canContinue <== accountIdField.text.delegate.mapToBool(validAccountId) and
    tokenField.text.delegate.mapToBool(_.nonEmpty)

  data.okPayWalletAccess <== accountIdField.text.delegate.zip(tokenField.text) {
    (id, address) => OkPayWalletAccess(id, address)
  }

  content = new VBox {
    styleClass += "okpay-pane"
    content = Seq(title, subtitle, dataPane)
  }

  private def validAccountId(text: String) = {
    val trimmedText = text.trim
    trimmedText.nonEmpty && trimmedText.matches(OkPaySettings.AccountIdPattern)
  }
}
