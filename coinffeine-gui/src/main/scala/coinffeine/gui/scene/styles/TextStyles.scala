package coinffeine.gui.scene.styles

import scalafx.scene.control.Labeled

object TextStyles {

  /** The URL to the stylesheet for these styles. */
  val StylesheetUrl = "/css/text.css"

  /** A heading 2 label. */
  trait H2 { this: Labeled => styleClass += "h2" }

  /** A relatively long text wrapped to expand on multiple lines. */
  trait TextWrapped { this: Labeled => styleClass += "text-wrapped" }
}
