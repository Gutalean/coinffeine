package coinffeine.gui.beans

import javafx.beans.binding.BooleanBinding
import scalafx.beans.property.{BooleanProperty, ReadOnlyBooleanProperty}

class BooleanBindingPimp(binding: BooleanBinding) {

  def toProperty: BooleanProperty = new BooleanProperty { this <== binding }

  def toReadOnlyProperty: ReadOnlyBooleanProperty = toProperty
}
