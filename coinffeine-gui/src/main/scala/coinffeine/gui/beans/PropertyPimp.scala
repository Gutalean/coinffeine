package coinffeine.gui.beans

import java.util.concurrent.Callable
import javafx.beans.InvalidationListener
import javafx.beans.binding.{BooleanBinding, Bindings, ObjectBinding}
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections.ObservableList

import coinffeine.common.properties.Property
import coinffeine.gui.util.FxExecutor._

class PropertyPimp[A](property: Property[A]) extends ObservableValue[A] {

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
