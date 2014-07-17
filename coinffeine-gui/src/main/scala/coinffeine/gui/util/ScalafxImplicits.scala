package coinffeine.gui.util

import javafx.beans.binding.{Binding, ObjectBinding}
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections

object ScalafxImplicits {

  implicit class ObservableValuePimp[T](val observableValue: ObservableValue[T]) extends AnyVal {

    /** Maps an observable value into a new one.
      *
      * Note: you should either bind the returned value or call {{{dispose()}}} to avoid leaking
      * memory.
      */
    def map[S](f: T => S): Binding[S] = new ObjectBinding[S] {
      super.bind(observableValue)

      override def computeValue(): S = f(observableValue.getValue)

      override def getDependencies = FXCollections.singletonObservableList(observableValue)

      override def dispose(): Unit = { super.unbind(observableValue) }
    }
  }
}
