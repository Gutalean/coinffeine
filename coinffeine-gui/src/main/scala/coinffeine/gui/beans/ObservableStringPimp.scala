package coinffeine.gui.beans

import java.util.concurrent.Callable
import javafx.beans.binding.{Bindings, StringBinding}
import javafx.beans.value.ObservableValue

class ObservableStringPimp(val observableValue: ObservableValue[String]) extends AnyVal {

  def toStr: StringBinding = Bindings.createStringBinding(
    new Callable[String] {
      override def call() = observableValue.getValue
    },
    observableValue)
}
