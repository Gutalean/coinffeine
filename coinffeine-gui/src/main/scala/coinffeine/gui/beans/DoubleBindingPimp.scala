package coinffeine.gui.beans

import javafx.beans.binding.DoubleBinding
import scalafx.beans.property.{DoubleProperty, ReadOnlyDoubleProperty}

class DoubleBindingPimp(binding: DoubleBinding) {

  def toProperty: DoubleProperty = new DoubleProperty { this <== binding }

  def toReadOnlyProperty: ReadOnlyDoubleProperty = toProperty
}
