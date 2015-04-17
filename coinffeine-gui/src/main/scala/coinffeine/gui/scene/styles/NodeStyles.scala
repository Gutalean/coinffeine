package coinffeine.gui.scene.styles

import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.Priority

import org.controlsfx.control.PopOver

/** Styles applicable to nodes. */
object NodeStyles {

  /** A node that expands horizontally. */
  trait HExpand { this: Node => hgrow = Priority.Always }

  /** A node that expands vertically. */
  trait VExpand { this: Node => vgrow = Priority.Always }

  /** A node that has a pop-over. */
  trait Poppable { this: Node =>

    private val _popOverContent = new ObjectProperty[javafx.scene.Node](this, "popOverContent", new Label(""))
    def popOverContent = _popOverContent
    def popOverContent_=(value: Node): Unit = {
      _popOverContent.value = value
    }

    private val popover = new PopOver {
      detachableProperty().set(false)
      arrowSizeProperty().set(6)
      arrowIndentProperty().set(4)
      contentNodeProperty().bind(popOverContent)
    }

    styleClass += "poppable"
    onMouseEntered = { (ev: MouseEvent) => popover.show(this) }
    onMouseExited = { (ev: MouseEvent) => popover.hide() }
  }
}
