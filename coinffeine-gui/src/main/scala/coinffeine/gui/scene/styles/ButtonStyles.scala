package coinffeine.gui.scene.styles

import scalafx.scene.control.Button

/** Mixin traits to manage button styles. */
object ButtonStyles {

  /** The URL to the stylesheet for these styles. */
  val StylesheetUrl = "/css/buttons.css"

  /** A button that represents an active action. */
  trait Action { this: Button => styleClass += "action-button" }
}
