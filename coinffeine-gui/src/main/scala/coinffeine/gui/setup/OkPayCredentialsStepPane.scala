package coinffeine.gui.setup

import coinffeine.gui.beans.Implicits._
import scalafx.scene.control.{PasswordField, TextField, Label}
import scalafx.scene.layout.{VBox, HBox}

import coinffeine.gui.control.{SupportWidget, GlyphIcon}
import coinffeine.gui.wizard.StepPane
import coinffeine.peer.payment.okpay.OkPayCredentials

class OkPayCredentialsStepPane(data: SetupConfig) extends StepPane[SetupConfig] {
  override val icon = GlyphIcon.Number2

  private val title = new Label("Configure your OKPay account") { styleClass += "title" }

  private val subtitle = new HBox {
    styleClass += "subtitle"
    children = Seq(
      new Label("We will use this credentials once to create an API token that will be " +
        "stored locally and never will be shared"),
      new SupportWidget("setup-credentials")
    )
  }

  private val emailField = new TextField() {
    promptText = "email@provider.com"
  }

  private val passwordField = new PasswordField()

  private val dataPane = new VBox {
    styleClass += "data"
    children = Seq(
      new Label("Your email"),
      emailField,
      new Label("Your password"),
      passwordField
    )
  }

  children = new VBox {
    styleClass += "okpay-pane"
    children = Seq(title, subtitle, dataPane)
  }

  data.okPayCredentials <==
    emailField.text.delegate.zip(passwordField.text)(OkPayCredentials.apply)

  canContinue <== emailField.text.delegate.mapToBool(validEmail) and
    passwordField.text.isEmpty.not

  private def validEmail(email: String): Boolean =
    email.matches("""^[\w-\.]+@([\w-]+\.)+\w+$""")
}
