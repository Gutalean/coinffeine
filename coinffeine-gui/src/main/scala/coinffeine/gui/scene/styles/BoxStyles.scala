package coinffeine.gui.scene.styles

import scalafx.scene.layout.{HBox, VBox}

/** Styles applicable to HBox and VBox containers. */
object BoxStyles {

  /** The URL to the stylesheet for these styles. */
  val StylesheetUrl = "/css/box.css"

  /** A button row comprised by several separated buttons in a horizontal layout. */
  trait ButtonRow { this: HBox => styleClass += "button-row" }

  /** A set of text paragraphs with some spacing among them. */
  trait Paragraphs { this: VBox => styleClass += "paragraphs" }

  /** A Text field with a companion button on its right. */
  trait TextFieldWithButton { this: HBox => styleClass += "textfield-with-button" }
}
