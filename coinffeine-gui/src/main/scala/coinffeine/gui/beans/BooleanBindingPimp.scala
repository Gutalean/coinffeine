package coinffeine.gui.beans

import javafx.beans.binding.BooleanBinding
import scalafx.beans.property.{ReadOnlyBooleanProperty, BooleanProperty}

class BooleanBindingPimp(binding: BooleanBinding) {

  def toProperty: BooleanProperty = new BooleanProperty { this <== binding }

  def toReadOnlyProperty: ReadOnlyBooleanProperty = toProperty
}
