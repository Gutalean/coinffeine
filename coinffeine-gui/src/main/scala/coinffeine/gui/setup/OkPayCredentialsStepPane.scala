package coinffeine.gui.setup

import scalafx.scene.control._
import scalafx.scene.layout.{HBox, VBox}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{FiatCurrencyChooser, GlyphIcon, SupportWidget}
import coinffeine.gui.wizard.StepPane

class OkPayCredentialsStepPane(data: SetupConfig, stepNumber: Int)
  extends StepPane[SetupConfig] {

  override val icon = GlyphIcon.Numbers(stepNumber)

  private val title = new Label("Configure your OKPay account") { styleClass += "title" }

  private val credentialsSubtitle = new HBox {
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

  private val credentialsPane = new VBox {
    styleClass += "data"
    children = Seq(
      new Label("Your email"),
      emailField,
      new Label("Your password"),
      passwordField
    )
  }

  private val currencySubtitle = new HBox {
    styleClass += "subtitle"
    children = Seq(new Label("Select the currency you want to operate with"))
  }

  private val currencyChooser = new FiatCurrencyChooser(data.currency.get)

  private val currencyPane = new HBox {
    styleClass += "data"
    children = Seq(new Label("Currency"), currencyChooser)
  }

  children = new VBox {
    styleClass += "okpay-pane"
    children = Seq(title, credentialsSubtitle, credentialsPane, currencySubtitle, currencyPane)
  }

  data.okPayCredentials <==
    emailField.text.delegate.zip(passwordField.text)(OkPayCredentials.apply)

  data.currency <== currencyChooser.currency

  canContinue <== emailField.text.delegate.map(validEmail).toBool and
    passwordField.text.isEmpty.not and
    currencyChooser.currency.delegate.map(_.isDefined).toBool

  private def validEmail(email: String): Boolean =
    email.matches("""^[\w-\.]+@([\w-]+\.)+\w+$""")
}
