package coinffeine.gui.control

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.scene.control.Label

import coinffeine.gui.control.ConnectionStatus._
import coinffeine.gui.util.ScalafxImplicits._

class ConnectionStatusWidget(status: ReadOnlyObjectProperty[ConnectionStatus]) extends Label {

  val statusColor = ObjectProperty[Color](Red)
  statusColor.bind(status.delegate.map(_.color))

  id = "connection-status"
  graphic = new StatusDisc(statusColor)
  text <== status.delegate.map(_.description)
}
