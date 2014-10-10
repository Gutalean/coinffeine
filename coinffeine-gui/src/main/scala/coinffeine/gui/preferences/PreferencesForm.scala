package coinffeine.gui.preferences

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, TabPane}
import scalafx.scene.layout.VBox
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.scene.{CoinffeineScene, Stylesheets}
import coinffeine.peer.config.SettingsProvider

class PreferencesForm(settingsProvider: SettingsProvider) {

  private val content = new VBox() {
    id = "preferences-root-pane"
    content = Seq(
      new TabPane() {
        tabs = Seq(new OkPayTab(settingsProvider))
      },
      new Button("Close") {
        onAction = { e: ActionEvent =>
          close()
        }
      }
    )
  }

  private val stage = new Stage(style = StageStyle.DECORATED) {
    title = "Coinffeine Preferences"
    resizable = false
    initModality(Modality.APPLICATION_MODAL)
    scene = new CoinffeineScene(Stylesheets.Preferences) {
      root = PreferencesForm.this.content
    }
    centerOnScreen()
  }

  private def close(): Unit = {
    stage.close()
  }

  def show(): Unit = {
    stage.showAndWait()
  }
}
