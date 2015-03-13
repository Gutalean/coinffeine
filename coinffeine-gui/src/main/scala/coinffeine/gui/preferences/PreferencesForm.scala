package coinffeine.gui.preferences

import scalafx.Includes
import scalafx.event.ActionEvent
import scalafx.scene.control.{Tab, Button, TabPane}
import scalafx.scene.layout.VBox
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.peer.config.SettingsProvider

class PreferencesForm(settingsProvider: SettingsProvider) extends Includes {

  private val tabs: Seq[PreferencesTab] = Seq(
    new OkPayTab(settingsProvider)
  )

  private val content = new VBox() {
    id = "preferences-root-pane"
    content = Seq(
      new TabPane() {
        tabs = PreferencesForm.this.tabs
      },
      new Button("Apply") {
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
    tabs.foreach(_.apply())
    stage.close()
  }

  def show(): Unit = {
    stage.showAndWait()
  }
}
