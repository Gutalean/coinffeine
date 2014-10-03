package coinffeine.gui.scene

import scalafx.scene.Scene

/** A scene that loads the Coinffiene style sheets and applies specific ones. */
class CoinffeineScene(additionalStyles: String*) extends Scene {

  stylesheets.add(Stylesheets.Main)
  stylesheets.add(Stylesheets.Controls)
  stylesheets.add(Stylesheets.Popup)

  additionalStyles.foreach(style => stylesheets.add(style))
}
