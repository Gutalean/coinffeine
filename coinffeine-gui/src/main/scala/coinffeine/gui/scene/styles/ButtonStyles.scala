package coinffeine.gui.scene.styles

import scalafx.scene.control.Button

/** Mixin traits to manage button styles. */
object ButtonStyles {

  /** The URL to the stylesheet for these styles. */
  val StylesheetUrl = "/css/buttons.css"

  /** A button that represents an active action. */
  trait Action { this: Button => styleClass += "action-button" }

  /** A rounded button. */
  trait Rounded { this: Button => styleClass += "rounded-button" }

  /** The details rounded button. */
  trait Details extends Rounded { this: Button => styleClass += "details-button" }

  /** The close rounded button. */
  trait Close extends Rounded { this: Button => styleClass += "close-button" }
}
