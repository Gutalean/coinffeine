package coinffeine.gui.application.help

import javafx.scene.paint.ImagePattern
import scalafx.Includes
import scalafx.event.ActionEvent
import scalafx.scene.Node
import scalafx.scene.control.{Button, Label}
import scalafx.scene.image.Image
import scalafx.scene.layout._
import scalafx.scene.shape.Circle
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.application.updates.CoinffeineVersion
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.Stylesheets

class AboutDialog extends Includes {

  private val content = new VBox() {
    id = "about-pane"
    content = Seq(
      new HBox() {
        hgrow = Priority.Always
        content = Seq(
          new VBox() {
            styleClass += "productinfo"
            content = Seq(
              new Label("Coinffeine") {
                styleClass += "title"
              },
              new StackPane { styleClass += "logo" },
              new Label(s"Version ${CoinffeineVersion.Current}"),
              new Label("Copyright (C) 2014-2015 Coinffeine S.L.")
            )
          },
          new VBox() {
            styleClass += "authorinfo"
            content = Seq(
              new Label("Coinffeine was created, coded and maintained by:"),
              new GridPane() {
                styleClass += "author-list"
                add(createAuthorInfo("Sebastián Ortega Torres", "graphics/avatar/sortega.jpg"), 0, 0)
                add(createAuthorInfo("Álvaro Polo Valdenebro", "graphics/avatar/apoloval.jpg"), 1, 0)
                add(createAuthorInfo("Alberto Gómez Toribio", "graphics/avatar/gotoalberto.jpg"), 0, 1)
                add(createAuthorInfo("Ximo Guanter Gonzálbez", "graphics/avatar/ximo.jpg"), 1, 1)
              }
            )
          }
        )
      },
      new Button("Close") {
        onAction = { e: ActionEvent => close() }
      }
    )
  }

  private val stage = new Stage(style = StageStyle.UTILITY) {
    title = "About Coinffeine"
    resizable = false
    initModality(Modality.APPLICATION_MODAL)
    scene = new CoinffeineScene(Stylesheets.About) {
      root = AboutDialog.this.content
    }
    centerOnScreen()
  }

  def show(): Unit = {
    stage.showAndWait()
  }

  private def close(): Unit = {
    stage.close()
  }

  private def createAuthorInfo(name: String, avatar: String): Node = {
    val nameLabel = new Label(name)
    val avatarCircle = new Circle() {
      radius = 30
      fill = new ImagePattern(new Image(avatar))
    }
    new VBox() {
      styleClass += "avatar"
      content = Seq(avatarCircle, nameLabel)
    }
  }
}
