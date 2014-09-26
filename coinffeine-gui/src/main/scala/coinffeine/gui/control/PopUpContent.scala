package coinffeine.gui.control

import scalafx.scene.Node

trait PopUpContent {
  protected def popupContent: Option[Node]
}

trait NoPopUpContent extends PopUpContent {
  protected override lazy val popupContent = None
}
