package coinffeine.gui.setup

import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.ButtonType

import coinffeine.gui.scene.CoinffeineAlert

/** What to do if the user tries to close the wizard */
private sealed trait ExitPolicy {
  def shouldClose(): Boolean
}

private object ExitPolicy {

  /** Ask for explicit confirmation using a dialog */
  object Confirmed extends ExitPolicy {
    override def shouldClose(): Boolean = {
      val dialog = new CoinffeineAlert(AlertType.Confirmation) {
        title = "Quit Coinffeine"
        headerText = "You will exit Coinffeine. Are you sure?"
        buttonTypes = Seq(ButtonType.Yes, ButtonType.No)
      }
      dialog.showAndWait().contains(ButtonType.Yes)
    }
  }

  /** Exit right away */
  object Unconfirmed extends ExitPolicy {
    override def shouldClose() = true
  }
}
