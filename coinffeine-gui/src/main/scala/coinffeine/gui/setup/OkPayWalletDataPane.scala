package coinffeine.gui.setup

import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.scene.control._
import scalafx.scene.layout._

import coinffeine.gui.wizard.StepPane
import coinffeine.peer.payment.okpay.OkPayWalletAccess

private[setup] class OkPayWalletDataPane extends StackPane with StepPane[SetupConfig] {

  private val walletIdProperty = new StringProperty(this, "walletId", "")
  walletIdProperty.onChange { updateAccess() }

  private val seedTokenProperty = new StringProperty(this, "seedToken", "")
  seedTokenProperty.onChange { updateAccess() }

  private val access = new ObjectProperty[Option[OkPayWalletAccess]](this, "access", None)

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
      add(new Label("Wallet ID"), 0, 0)
      add(new TextField() {
        id = "email"
        text <==> walletIdProperty
      }, 1, 0)
      add(new Label("Seed token"), 0, 1)
      add(new PasswordField() {
        id = "password"
        text <==> seedTokenProperty
      }, 1, 1)
    }

    new VBox(spacing = 5) {
      styleClass += "wizard-base-pane"
      content = Seq(
        new Label("Configure your OKPay account") { styleClass = Seq("wizard-step-title") },
        new Label("Please insert your OKPay API information"),
        grid
      )
    }
  }

  override def bindTo(data: ObjectProperty[SetupConfig]): Unit = {
    canContinue.value = true
    access.onChange {
      data.value = data.value.copy(okPayWalletAccess = access.value)
    }
  }

  private def updateAccess(): Unit = {
    access.value = (walletIdProperty.value, seedTokenProperty.value) match {
      case (walletId, seedToken) if !walletId.isEmpty && !seedToken.isEmpty =>
        Some(OkPayWalletAccess(walletId, seedToken))
      case _ => None
    }
  }
}
