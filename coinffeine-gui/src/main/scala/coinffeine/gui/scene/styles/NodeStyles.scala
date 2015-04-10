package coinffeine.gui.scene.styles

import scalafx.scene.Node
import scalafx.scene.layout.Priority

/** Styles applicable to nodes. */
object NodeStyles {

  /** A node that expands horizontally. */
  trait HExpand { this: Node => hgrow = Priority.Always }

  /** A node that expands vertically. */
  trait VExpand { this: Node => vgrow = Priority.Always }
}
