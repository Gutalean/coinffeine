package coinffeine.gui.control

import coinffeine.gui.scene.styles.PaneStyles

import scalafx.Includes._
import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.control.Label
import scalafx.scene.layout.HBox

import coinffeine.gui.beans.Implicits._

class ConnectionStatusWidget(
    status: ReadOnlyObjectProperty[ConnectionStatus]) extends HBox with PaneStyles.MinorSpacing {

  content = Seq(
    new StatusDisc() {
      failure <== status.delegate.mapToBool(!_.connected)
    },
    new Label {
      id = "connection-status"
      text <== status.delegate.map(_.description)
    }
  )
}
