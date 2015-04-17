package coinffeine.gui.scene.styles

import scalafx.Includes._
import scalafx.scene.Node
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

    /** The first time a pop-over is displayed derived classes are asked for its contents. */
    def popOverContent: Node

    private val popover = new PopOver(popOverContent) {
      detachableProperty().set(false)
      arrowSizeProperty().set(6)
      arrowIndentProperty().set(4)
    }

    styleClass += "poppable"
    onMouseEntered = { (ev: MouseEvent) => popover.show(this) }
    onMouseExited = { (ev: MouseEvent) => popover.hide() }
  }
}
