package coinffeine.gui.scene.styles

import scalafx.scene.control.Labeled

object TextStyles {

  /** The URL to the stylesheet for these styles. */
  val StylesheetUrl = "/css/text.css"

  /** A text representing a currency amount (without symbol). */
  trait CurrencyAmount { this: Labeled => styleClass += "currency-amount" }

  /** A text representing a currency symbol. */
  trait CurrencySymbol { this: Labeled => styleClass += "currency-symbol" }

  /** A text with emphasis. */
  trait Emphasis { this: Labeled => styleClass += "emphasis" }
}
