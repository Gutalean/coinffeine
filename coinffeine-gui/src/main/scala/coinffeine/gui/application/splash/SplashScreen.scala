package coinffeine.gui.application.splash

import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.control.Label
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.stage.StageStyle

import coinffeine.gui.application.updates.CoinffeineVersion
import coinffeine.gui.control.Spinner
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.Stylesheets

object SplashScreen {

  def displayOn(stage: PrimaryStage): Unit = {
    stage.initStyle(StageStyle.TRANSPARENT)
    stage.scene = new CoinffeineScene(Stylesheets.Splash) {
      fill = Color.Transparent
      root = new VBox {
        id = "splash-root"
        content = Seq(
          new Spinner(autoPlay = true),
          new HBox {
            content = Seq(
              new Label("Copyright (C) 2014-2015 Coinffeine S.L.") {
                id = "copyright-note"
              },
              new Label(s"Version ${CoinffeineVersion.Current}") {
                id = "version-note"
              })
          })
      }
    }
    stage.show()
  }
}
