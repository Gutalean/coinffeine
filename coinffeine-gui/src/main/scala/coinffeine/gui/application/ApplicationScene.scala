package coinffeine.gui.application

import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafx.scene.layout.BorderPane
import scalafx.scene.{Node, Parent}

import org.controlsfx.control.SegmentedButton

import coinffeine.gui.preferences.PreferencesForm
import coinffeine.gui.scene.{Stylesheets, CoinffeineScene}
import coinffeine.peer.config.SettingsProvider

/** Main scene of the application.
  *
  * @param views  Available application views. The first one is visible at application start.
  * @param toolbarWidgets  Widgets displayed at the tool bar on the top of the window
  * @param statusBarWidgets  Widgets displayed on the status bar at the bottom of the window
  */
class ApplicationScene(views: Seq[ApplicationView],
                       toolbarWidgets: Seq[Node],
                       statusBarWidgets: Seq[Node],
                       settingsProvider: SettingsProvider)
    extends CoinffeineScene(Stylesheets.Operations, Stylesheets.Stats, Stylesheets.Wallet) {

  require(views.nonEmpty, "At least one view is required")

  val menuBar = new MenuBar {
    useSystemMenuBar = true

    menus = Seq(
      new Menu("Edit") {
        items = Seq(
          new MenuItem("Preferences") {
            onAction = { e: ActionEvent =>
              val form = new PreferencesForm(settingsProvider)
              form.show()
            }
          }
        )
      }
    )
  }

  val currentView = new ObjectProperty[ApplicationView](this, "currentView", null)

  private val viewSelector: Parent = {
    val selector = new SegmentedButton {
      setId("view-selector")
    }
    val buttons = for (view <- views) yield new ToggleButton(view.name) {
      disable <== selected
      handleEvent(ActionEvent.Action) { () => currentView.value = view }
    }
    buttons.foreach(b => selector.getButtons.add(b))
    buttons.head.selected = true
    selector
  }

  private val toolbarPane = new ToolBar {
    id = "toolbar"
    content = Seq(viewSelector, new Separator()) ++ toolbarWidgets
  }

  private val statusBarPane = new ToolBar {
    id = "status"
    prefHeight = 25
    content = interleaveSeparators(statusBarWidgets)
  }

  root = {
    val mainPane = new BorderPane {
      id = "main-root-pane"
      top = toolbarPane
      bottom = statusBarPane
      children.add(menuBar)
    }
    currentView.onChange { mainPane.center = currentView.value.centerPane }
    mainPane
  }

  currentView.value = views.head

  private def interleaveSeparators(widgets: Seq[Node]): Seq[Node] =
    widgets.flatMap(w => Seq(new Separator, w)).drop(1)
}
