package coinffeine.gui.beans

import java.util.concurrent.Callable
import javafx.beans.binding.{Bindings, DoubleBinding}
import javafx.beans.value.ObservableValue

class ObservableDoublePimp(val observableValue: ObservableValue[Double]) extends AnyVal {

  def toDouble: DoubleBinding = Bindings.createDoubleBinding(
    new Callable[java.lang.Double] {
      override def call() = observableValue.getValue
    },
    observableValue)
}
