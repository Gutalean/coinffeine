package coinffeine.gui.util

import javafx.beans.{InvalidationListener, Observable}
import javafx.beans.binding._
import javafx.beans.value.ObservableValue
import javafx.collections.{ObservableList, FXCollections}

import coinffeine.model.properties.{Cancellable, Property}

object ScalafxImplicits {

  import FxExecutor.asContext

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

    def mapToBool(f: T => Boolean): BooleanBinding = new BooleanBinding {
      super.bind(observableValue)

      override def computeValue(): Boolean = f(observableValue.getValue)

      override def getDependencies = FXCollections.singletonObservableList(observableValue)

      override def dispose(): Unit = { super.unbind(observableValue) }
    }
  }

  class ObservableProperty[A](val property: Property[A]) extends Observable {

    private var listeners: Map[InvalidationListener, Cancellable] = Map.empty

    override def addListener(listener: InvalidationListener): Unit = {
      val cancellation = property.onNewValue(_ => listener.invalidated(this))
      listeners += listener -> cancellation
    }

    override def removeListener(listener: InvalidationListener): Unit = {
      listeners -= listener
    }
  }

  implicit class PimpMyProperty[A](property: Property[A]) {

    private val observable = new ObservableProperty(property)

    def map[B](f: A => B): ObjectBinding[B] = new ObjectBinding[B] {
      override def computeValue() = f(observable.property.get)
      override def getDependencies = FXCollections.singletonObservableList(observable)
      override def dispose(): Unit = { super.unbind(observable) }

      super.bind(observable)
    }

    def mapToBoolean(f: A => Boolean): BooleanBinding = new BooleanBinding {
      override def computeValue() = f(property.get)
      override def getDependencies = FXCollections.singletonObservableList(observable)
      override def dispose(): Unit = { super.unbind(observable) }

      super.bind(observable)
    }

    def bindObservableList[B](list: ObservableList[B])(f: A => Seq[B]): Unit = {
      property.onNewValue { newValue =>
        val contents = f(newValue)
        list.setAll(contents: _*)
      }
      list.setAll(f(property.get): _*) // ensure last values are set
    }

    implicit def toScalaFx: Binding[A] = map(identity[A])
  }
}
