package coinffeine.gui.scene.styles

import scalafx.scene.control.Labeled

object TextStyles {

  /** The URL to the stylesheet for these styles. */
  val StylesheetUrl = "/css/text.css"

  /** A heading 2 label. */
  trait H2 { this: Labeled => styleClass += "h2" }

  /** A relatively long text wrapped to expand on multiple lines. */
  trait TextWrapped { this: Labeled => styleClass += "text-wrapped" }

  /** A light text labeled. */
  trait Light { this: Labeled => styleClass += "light" }

  /** A boldface text labeled. */
  trait Boldface { this: Labeled => styleClass += "boldface" }

  /** A boldface text labeled. */
  trait SuperBoldface { this: Labeled => styleClass += "super-boldface" }

  /** A semi-big sized text labeled. */
  trait SemiBig { this: Labeled => styleClass += "semibig" }

  /** A big sized text labeled. */
  trait Big { this: Labeled => styleClass += "big" }

  /** A huge sized text labeled. */
  trait Huge { this: Labeled => styleClass += "huge" }

  /** A text representing good news. */
  trait GoodNews { this: Labeled => styleClass += "good-news" }

  /** A text representing bad news. */
  trait BadNews { this: Labeled => styleClass += "bad-news" }

  /** A text representing neutral news. */
  trait NeutralNews { this: Labeled => styleClass += "neutral-news" }
}
