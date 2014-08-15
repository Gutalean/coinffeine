package coinffeine.gui.application

import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.event.ActionEvent
import scalafx.scene.control.{Separator, ToggleButton, ToolBar}
import scalafx.scene.layout.BorderPane
import scalafx.scene.{Node, Parent, Scene}

import org.controlsfx.control.SegmentedButton

import coinffeine.gui.application.ApplicationScene._

/** Main scene of the application.
  *
  * @param views  Available application views. The first one is visible at application start.
  * @param toolbarWidgets  Widgets displayed at the tool bar on the top of the window
  * @param statusBarWidgets  Widgets displayed on the status bar at the bottom of the window
  */
class ApplicationScene(views: Seq[ApplicationView],
                       toolbarWidgets: Seq[Node],
                       statusBarWidgets: Seq[Node])
  extends Scene(width = DefaultWidth, height = DefaultHeight) {

  require(views.nonEmpty, "At least one view is required")

  stylesheets.add("/css/main.css")

  val currentView = new ObjectProperty[ApplicationView](this, "currentView", null)

  private val viewSelector: Parent = {
    val selector = new SegmentedButton {
      setId("view-selector")
    }
    val buttons = for (view <- views) yield new ToggleButton(view.name) {
      disable <== selected
      handleEvent(ActionEvent.ACTION) { () => currentView.value = view }
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
      top = toolbarPane
      bottom = statusBarPane
    }
    currentView.onChange { mainPane.center = currentView.value.centerPane }
    mainPane
  }

  currentView.value = views.head

  private def interleaveSeparators(widgets: Seq[Node]): Seq[Node] =
    widgets.flatMap(w => Seq(new Separator, w)).drop(1)
}

object ApplicationScene {
  val DefaultWidth = 600
  val DefaultHeight = 400
}
