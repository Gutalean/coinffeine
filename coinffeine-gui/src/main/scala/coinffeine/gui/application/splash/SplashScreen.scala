package coinffeine.gui.application.splash

import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.control.Label
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.stage.StageStyle

import coinffeine.gui.control.Spinner
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.peer.AppVersion

class SplashScreen(techPreview: Boolean) {

  def displayOn(stage: PrimaryStage): Unit = {
    stage.initStyle(StageStyle.TRANSPARENT)
    stage.title = "Coinffeine"
    stage.scene = new CoinffeineScene(Stylesheets.Splash) {
      fill = Color.Transparent
      root = new VBox {
        id = "splash-root"
        children = Seq(
          new Spinner(autoPlay = true),
          new HBox(
            new Label("Copyright (C) 2014-2015 Coinffeine S.L.") { id = "copyright-note" },
            new Label(versionText) { id = "version-note" }
          ))
      }
    }
    stage.show()
  }

  private def versionText: String = "%sVersion %s".format(
    if (techPreview) "Technical Preview " else "",
    AppVersion.Current
  )
}
