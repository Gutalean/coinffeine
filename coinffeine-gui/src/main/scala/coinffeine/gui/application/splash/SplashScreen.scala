package coinffeine.gui.application.splash

import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.control.{Label, ProgressBar}
import scalafx.scene.layout._
import scalafx.stage.StageStyle

import coinffeine.gui.application.updates.CoinffeineVersion
import coinffeine.gui.scene.{CoinffeineScene, Stylesheets}

object SplashScreen {

  def displayOn(stage: PrimaryStage): Unit = {
    stage.initStyle(StageStyle.UNDECORATED)
    stage.scene = new CoinffeineScene(Stylesheets.Splash) {
      root = new VBox {
        id = "splash-root"
        content = Seq(
          new ProgressBar { progress = -1 },
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
