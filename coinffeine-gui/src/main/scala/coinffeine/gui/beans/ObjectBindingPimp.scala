package coinffeine.gui.beans

import javafx.beans.binding.ObjectBinding
import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

class ObjectBindingPimp[A](binding: ObjectBinding[A]) {

  def toProperty: ObjectProperty[A] = new ObjectProperty[A] { this <== binding }

  def toReadOnlyProperty: ReadOnlyObjectProperty[A] = toProperty
}
