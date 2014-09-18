package coinffeine.gui.util

import java.util.concurrent.Callable
import javafx.beans.{InvalidationListener, Observable}
import javafx.beans.binding._
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections.{MapChangeListener, ObservableMap, ObservableList, FXCollections}

import coinffeine.model.properties.{PropertyMap, Cancellable, Property}

object ScalafxImplicits {

  import FxExecutor.asContext

  implicit class ObservableValuePimp[T](val observableValue: ObservableValue[T]) extends AnyVal {

    /** Maps an observable value into a new one.
      *
      * Note: you should either bind the returned value or call {{{dispose()}}} to avoid leaking
      * memory.
      */
    def map[S](f: T => S): ObjectBinding[S] = Bindings.createObjectBinding(
      new Callable[S] {
        override def call() = f(observableValue.getValue)
      },
      observableValue)

    def mapToBool(f: T => Boolean): BooleanBinding = Bindings.createBooleanBinding(
      new Callable[java.lang.Boolean] {
        override def call() = f(observableValue.getValue)
      },
      observableValue)
  }

  implicit class PropertyPimp[A](property: Property[A]) extends ObservableValue[A] {

    private val invalidationListeners = new CancellableListeners[InvalidationListener]
    private val changeListeners = new CancellableListeners[ChangeListener[_]]

    override def addListener(listener: InvalidationListener): Unit = {
      invalidationListeners.add(listener, property.onNewValue(_ => listener.invalidated(this)))
    }

    override def removeListener(listener: InvalidationListener): Unit = {
      invalidationListeners.cancel(listener)
    }

    override def getValue = property.get

    override def addListener(listener: ChangeListener[_ >: A]) = {
      changeListeners.add(listener, property.onChange { (oldVal, newVal) =>
        listener.changed(this, oldVal, newVal)
      })
    }

    override def removeListener(listener: ChangeListener[_ >: A]) = {
      changeListeners.cancel(listener)
    }

    def map[B](f: A => B): ObjectBinding[B] = Bindings.createObjectBinding(
      new Callable[B] {
        override def call() = f(property.get)
      },
      this
    )

    def mapToBoolean(f: A => Boolean): BooleanBinding = Bindings.createBooleanBinding(
      new Callable[java.lang.Boolean] {
        override def call() = f(property.get)
      },
      this
    )

    def bindToList[B](list: ObservableList[B])(f: A => Seq[B]): Unit = {
      property.onNewValue { newValue =>
        val contents = f(newValue)
        list.setAll(contents: _*)
      }
      list.setAll(f(property.get): _*) // ensure last values are set
    }
  }

  private class CancellableListeners[L] {

    private var listeners: Map[L, Cancellable] = Map.empty

    def add(listener: L, cancellable: Cancellable): Unit = {
      listeners += listener -> cancellable
    }

    def cancel(listener: L): Unit = {
      listeners.get(listener).map(_.cancel())
      listeners -= listener
    }
  }
}
