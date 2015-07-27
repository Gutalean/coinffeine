package coinffeine.gui.beans

import java.util.concurrent.Callable
import javafx.beans.binding.{Bindings, IntegerBinding}
import javafx.beans.value.ObservableValue

class ObservableIntegerPimp(val observableValue: ObservableValue[Int]) extends AnyVal {

  def toInt: IntegerBinding = Bindings.createIntegerBinding(
    new Callable[Integer] {
      override def call() = observableValue.getValue
    },
    observableValue)
}
