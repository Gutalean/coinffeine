package coinffeine.gui.beans

import javafx.beans.binding.StringBinding
import scalafx.beans.property.{ReadOnlyStringProperty, StringProperty}

class StringBindingPimp(binding: StringBinding) {

  def toProperty: StringProperty = new StringProperty { this <== binding }

  def toReadOnlyProperty: ReadOnlyStringProperty = toProperty
}
