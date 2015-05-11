package coinffeine.gui.control

import scalafx.scene.control.Label
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout.HBox

/** An image and a label in an horizontal layout.
  *
  * This control provides a replacement for a label with an embedded image in buggy JavaFX versions.
  */
class ImgLabel(initialImage: Image, initialText: String) extends HBox {

  private val img = new ImageView(initialImage)
  private val label = new Label(initialText)

  val image = img.image
  val text = label.text

  children = Seq(img, label)
}
