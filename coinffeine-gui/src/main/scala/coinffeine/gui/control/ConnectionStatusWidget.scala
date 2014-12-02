package coinffeine.gui.control

import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.control.Label
import scalafx.scene.layout.HBox

import coinffeine.gui.beans.Implicits._

class ConnectionStatusWidget(status: ReadOnlyObjectProperty[ConnectionStatus]) extends HBox(3) {
  content = Seq(
    new StatusDisc(status.delegate.map(_.color)),
    new Label {
      id = "connection-status"
      text <== status.delegate.map(_.description)
    }
  )
}
