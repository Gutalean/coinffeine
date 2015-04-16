package coinffeine.gui.beans

import scalafx.beans.property.{DoubleProperty, ReadOnlyDoubleProperty}
import javafx.beans.binding.DoubleBinding

class DoubleBindingPimp(binding: DoubleBinding) {

  def toProperty: DoubleProperty = new DoubleProperty { this <== binding }

  def toReadOnlyProperty: ReadOnlyDoubleProperty = toProperty
}
