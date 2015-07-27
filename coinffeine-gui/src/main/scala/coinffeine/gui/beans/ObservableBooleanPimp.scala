package coinffeine.gui.beans

import java.util.concurrent.Callable
import javafx.beans.binding.{Bindings, BooleanBinding}
import javafx.beans.value.ObservableValue

class ObservableBooleanPimp(val observableValue: ObservableValue[Boolean]) extends AnyVal {

  def toBool: BooleanBinding = Bindings.createBooleanBinding(
    new Callable[java.lang.Boolean] {
      override def call() = observableValue.getValue
    },
    observableValue)
}
