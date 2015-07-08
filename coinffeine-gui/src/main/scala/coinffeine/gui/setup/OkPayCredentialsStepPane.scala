package coinffeine.gui.setup

import scalafx.scene.control.{Label, PasswordField, TextField}
import scalafx.scene.layout.{HBox, VBox}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, SupportWidget}
import coinffeine.gui.wizard.StepPane

class OkPayCredentialsStepPane(data: SetupConfig, stepNumber: Int)
  extends StepPane[SetupConfig] {

  override val icon = GlyphIcon.Numbers(stepNumber)

  private val title = new Label("Configure your OKPay account") { styleClass += "title" }

  private val subtitle = new HBox {
    styleClass += "subtitle"
    children = Seq(
      new Label("We will use this credentials once to create an API token that will be " +
        "stored locally and never will be shared. Double factor authentication should be " +
        "disabled during this process"),
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
