package coinffeine.gui.scene.styles

import scalafx.scene.layout.{Pane, HBox, VBox}

/** Styles applicable to panes. */
object PaneStyles {

  /** The URL to the stylesheet for these styles. */
  val StylesheetUrl = "/css/pane.css"

  /** A button row comprised by several separated buttons in a horizontal layout. */
  trait ButtonRow { this: HBox => styleClass += "button-row" }

  /** A box with some minor spacing */
  trait MinorSpacing { this: Pane => styleClass += "minor-spacing" }

  /** A set of text paragraphs with some spacing among them. */
  trait Paragraphs { this: VBox => styleClass += "paragraphs" }

  /** A Text field with a companion button on its right. */
  trait TextFieldWithButton { this: HBox => styleClass += "textfield-with-button" }
}
