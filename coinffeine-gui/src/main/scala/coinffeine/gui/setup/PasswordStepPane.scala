package coinffeine.gui.setup

import scalafx.beans.property.ObjectProperty
import scalafx.event.Event
import scalafx.geometry.Pos
import scalafx.scene.control.{Label, PasswordField, RadioButton, ToggleGroup}
import scalafx.scene.layout._
import scalafx.scene.text.TextAlignment

import coinffeine.gui.control.GlyphIcon
import coinffeine.gui.wizard.StepPane

private[setup] class PasswordStepPane extends StackPane with StepPane[SetupConfig] {

  override val icon = GlyphIcon.Coinffeine

  private val passwordValidator = new PasswordValidator

  /** Mutable password to be updated when controls produce events */
  private val password = new ObjectProperty[Option[String]](this, "password", None)

  private val group = new ToggleGroup()
  private val usePasswordButton = new RadioButton {
    id = "usePassword"
    text = "Use a password"
    toggleGroup = group
    selected = true
    handleEvent(Event.ANY) { () => handlePasswordChange()  }
  }
  private val noPasswordButton = new RadioButton {
    id = "noPassword"
    text = "Don't use any password"
    toggleGroup = group
    handleEvent(Event.ANY) { () => handlePasswordChange() }
  }
  private val noPasswordProperty = noPasswordButton.selected

  private val passwordField = new PasswordField() {
    id = "passwordField"
    disable <== noPasswordProperty
    handleEvent(Event.ANY) { () => handlePasswordChange() }
  }
  private val repeatPasswordField = new PasswordField() {
    id = "repeatPasswordField"
    disable <== noPasswordProperty
    handleEvent(Event.ANY) { () => handlePasswordChange() }
  }
  private val passwordWarningLabel = new Label() {
    id = "wizard-password-warn-label"
    styleClass.add("wizard-error-label")
    textAlignment = TextAlignment.Center
    alignment = Pos.TopCenter
    disable <== noPasswordProperty
  }

  private val passwordPane = new GridPane {
    id = "wizard-password-credentials-pane"
    columnConstraints = Seq(
      new ColumnConstraints {
        minWidth = 130
        hgrow = Priority.Never
      },
      new ColumnConstraints { hgrow = Priority.Always }
    )
    add(new Label("Password") { disable <== noPasswordProperty }, 0, 0)
    add(passwordField, 1, 0)
    add(new Label("Repeat password") { disable <== noPasswordProperty }, 0, 1)
    add(repeatPasswordField, 1, 1)
    add(passwordWarningLabel, 0, 2, 2, 1)
  }

  content = new VBox() {
    styleClass += "wizard-base-pane"
    content = Seq(
      new Label("Choose a password") { styleClass.add("wizard-step-title") },
      new Label("You can use a password to protect the information that " +
         "Coinffeine saves in your computer.") {
        wrapText = true
      },
      new VBox() {
        id = "wizard-password-inputs-pane"
        content = Seq(usePasswordButton, passwordPane, noPasswordButton)
      }
    )
  }

  /** Translates updates on the password property to the setupConfig property */
  override def bindTo(setupConfig: ObjectProperty[SetupConfig]): Unit = {
    password.onChange {
      setupConfig.value = setupConfig.value.copy(password.value)
    }
  }

  private def handlePasswordChange(): Unit = {
    val pass1 = passwordField.text.value
    val pass2 = repeatPasswordField.text.value
    val passwordsMatch = pass1 == pass2
    password.value = if (noPasswordProperty.value) None else Some(pass1)
    canContinue.value = noPasswordProperty.value || (!pass1.isEmpty && passwordsMatch)
    passwordWarningLabel.text = Seq(
      if (!pass1.isEmpty && passwordValidator.isWeak(pass1)) Some("password is weak") else None,
      if (!pass2.isEmpty && !passwordsMatch) Some("passwords don't match") else None
    ).flatten.mkString(" and ")
  }
}
