package coinffeine.gui.scene

import scalafx.scene.Scene

import coinffeine.gui.scene.styles.{ButtonStyles, TextStyles, PaneStyles, Stylesheets}

/** A scene that loads the Coinffeine style sheets and applies specific ones. */
class CoinffeineScene(additionalStyles: String*) extends Scene {

  stylesheets.add(Stylesheets.Fonts)
  stylesheets.add(Stylesheets.Palette)

  stylesheets.add(Stylesheets.Controls)

  stylesheets.add(ButtonStyles.StylesheetUrl)
  stylesheets.add(PaneStyles.StylesheetUrl)
  stylesheets.add(TextStyles.StylesheetUrl)

  stylesheets.add(Stylesheets.Main)
  stylesheets.add(Stylesheets.Popup)

  additionalStyles.foreach(style => stylesheets.add(style))
}
