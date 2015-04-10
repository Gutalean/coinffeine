package coinffeine.gui.scene.styles

import scalafx.scene.layout.{GridPane, Pane, HBox, VBox}

/** Styles applicable to panes. */
object PaneStyles {

  /** The URL to the stylesheet for these styles. */
  val StylesheetUrl = "/css/pane.css"

  /** A button row comprised by several separated buttons in a horizontal layout. */
  trait ButtonRow { this: HBox => styleClass += "button-row" }

  /** A pane with its contents aligned in the center. */
  trait Centered { this: Pane => styleClass += "centered" }

  /** A inner pane with some padding respect its parent. */
  trait Inner { this: Pane => styleClass += "inner" }

  /** A inner pane with empathized lateral margins. */
  trait InnerWithMargins { this: Pane => styleClass += "inner-with-margins" }

  /** A box with some minor spacing */
  trait MinorSpacing { this: Pane => styleClass += "minor-spacing" }

  /** A set of text paragraphs with some spacing among them. */
  trait Paragraphs { this: VBox => styleClass += "paragraphs" }

  trait SpacedGrid { this: GridPane => styleClass += "spaced-grid" }

  /** An hbox that acts as a status bar at the bottom of the window. */
  trait StatusBar { this: HBox => styleClass += "status-bar" }

  /** A Text field with a companion button on its right. */
  trait TextFieldWithButton { this: HBox => styleClass += "textfield-with-button" }
}
