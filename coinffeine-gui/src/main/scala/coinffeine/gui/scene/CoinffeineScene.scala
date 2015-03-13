package coinffeine.gui.scene

import scalafx.scene.Scene

import coinffeine.gui.scene.styles.Stylesheets

/** A scene that loads the Coinffeine style sheets and applies specific ones. */
class CoinffeineScene(additionalStyles: String*) extends Scene {

  stylesheets.add(Stylesheets.Main)
  stylesheets.add(Stylesheets.Controls)
  stylesheets.add(Stylesheets.Popup)

  additionalStyles.foreach(style => stylesheets.add(style))
}
