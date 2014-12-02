package coinffeine.gui.beans

import scalafx.beans.property.{BooleanProperty, ReadOnlyBooleanProperty}

object ObservableConstants {

  /** An observable boolean value of true that never changes. */
  val True: ReadOnlyBooleanProperty = BooleanProperty(value = true)

  /** An observable boolean value of false that never changes. */
  val False: ReadOnlyBooleanProperty = BooleanProperty(value = false)
}
