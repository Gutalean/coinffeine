package coinffeine.gui.setup

import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.scene.control._
import scalafx.scene.layout._

import coinffeine.gui.control.GlyphIcon
import coinffeine.gui.wizard.StepPane
import coinffeine.peer.payment.okpay.OkPayCredentials

private[setup] class OkPayCredentialsStepPane extends StackPane with StepPane[SetupConfig] {

  override val icon = GlyphIcon.Coinffeine

  private val emailProperty = new StringProperty(this, "email", "")
  emailProperty.onChange { updateCredentials() }
  private val passwordProperty = new StringProperty(this, "password", "")
  passwordProperty.onChange { updateCredentials() }
  private val credentials = new ObjectProperty[Option[OkPayCredentials]](this, "credentials", None)

  content = {
    val grid = new GridPane {
      id = "wizard-okpay-inputs-pane"
      columnConstraints = Seq(new ColumnConstraints {
        prefWidth = 100
        fillWidth = false
        hgrow = Priority.Never
      }, new ColumnConstraints {
        fillWidth = true
        hgrow = Priority.Always
      })
      add(new Label("Email"), 0, 0)
      add(new TextField() {
        id = "email"
        text <==> emailProperty
      }, 1, 0)
      add(new Label("Password"), 0, 1)
      add(new PasswordField() {
        id = "password"
        text <==> passwordProperty
      }, 1, 1)
    }

    new VBox(spacing = 5) {
      styleClass += "wizard-base-pane"
      content = Seq(
        new Label("Configure your OKPay account") { styleClass = Seq("wizard-step-title") },
        new Label("Your credentials are stored locally and never will be shared"),
        grid
      )
    }
  }

  override def bindTo(data: ObjectProperty[SetupConfig]): Unit = {
    canContinue.value = true
    credentials.onChange {
      data.value = data.value.copy(okPayCredentials = credentials.value)
    }
  }

  private def updateCredentials(): Unit = {
    credentials.value = (emailProperty.value, passwordProperty.value) match {
      case (email, password) if !email.isEmpty && !password.isEmpty =>
        Some(OkPayCredentials(email, password))
      case _ => None
    }
  }
}
