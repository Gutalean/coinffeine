package coinffeine.gui.scene

import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType

import coinffeine.gui.scene.styles.{ButtonStyles, Stylesheets}

class CoinffeineAlert(alertType: AlertType,
                      prefSize: Option[(Int, Int)] = None) extends Alert(alertType) {

  val stylesheets = dialogPane.value.getStylesheets

  stylesheets.add(Stylesheets.Fonts)
  stylesheets.add(Stylesheets.Palette)
  stylesheets.add(Stylesheets.Controls)
  stylesheets.add(Stylesheets.Dialogs)
  stylesheets.add(ButtonStyles.StylesheetUrl)

  // FIXME: remove this dirty fix when dialog auto-sizing is fixed in Linux
  //        See https://javafx-jira.kenai.com/browse/RT-40230
  resizable = true
  prefSize.foreach { case (w, h) => dialogPane.value.setPrefSize(w, h) }
}
