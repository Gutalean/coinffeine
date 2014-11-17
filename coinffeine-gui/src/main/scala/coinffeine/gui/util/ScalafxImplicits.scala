package coinffeine.gui.util

import scala.collection.JavaConversions._

import java.util.concurrent.Callable
import javafx.beans.binding._
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.beans.{InvalidationListener, Observable}
import javafx.collections.ObservableList

import scalafx.beans.property._
import scalafx.collections.ObservableBuffer

import coinffeine.model.properties.{Cancellable, Property, PropertyMap}

object ScalafxImplicits {

  import FxExecutor.asContext

  implicit class ObservableValuePimp[A](val observableValue: ObservableValue[A]) extends AnyVal {

    /** Maps an observable value into a new one.
      *
      * Note: you should either bind the returned value or call {{{dispose()}}} to avoid leaking
      * memory.
      */
    def map[S](f: A => S): ObjectBinding[S] = Bindings.createObjectBinding(
      new Callable[S] {
        override def call() = f(observableValue.getValue)
      },
      observableValue)

    def mapToString(f: A => String): StringBinding = Bindings.createStringBinding(
      new Callable[String] {
        override def call() = f(observableValue.getValue)
      },
      observableValue)

    def mapToBool(f: A => Boolean): BooleanBinding = Bindings.createBooleanBinding(
      new Callable[java.lang.Boolean] {
        override def call() = f(observableValue.getValue)
      },
      observableValue)

    def bindToList[B](list: ObservableList[B])(f: A => Seq[B]): Unit = {
      observableValue.addListener(new ChangeListener[A] {
        override def changed(observable: ObservableValue[_ <: A], oldValue: A, newValue: A) = {
          list.setAll(f(newValue): _*)
        }
      })
      list.setAll(f(observableValue.getValue): _*) // ensure last values are set
    }
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

  implicit class PropertyMapPimp[K, V](property: PropertyMap[K, V]) extends Observable {

    private val listeners = new CancellableListeners[InvalidationListener]

    override def addListener(listener: InvalidationListener) = {
      listeners.add(listener, property.onNewValue((_, _) => listener.invalidated(this)))
    }

    override def removeListener(listener: InvalidationListener) = {
      listeners.cancel(listener)
    }

    def mapEntry[B](key: K)(f: V => B): ObjectBinding[Option[B]] = Bindings.createObjectBinding(
      new Callable[Option[B]] {
        override def call() = property.get(key).map(f)
      },
      this)
  }

  implicit class ObservableBufferPimp[A](buffer: ObservableBuffer[A]) {
    def bindToList[B](other: ObservableList[B])(f: A => B): Unit = {
      other.clear()
      other.addAll(buffer.map(f))
      buffer.onChange { (_, changes) =>
        changes.foreach {
          case ObservableBuffer.Add(pos, elems: Traversable[A]) =>
            other.addAll(pos, elems.map(f).toSeq)
          case ObservableBuffer.Remove(pos, elems: Traversable[A]) =>
            other.remove(pos, pos + elems.size)
          case ObservableBuffer.Reorder(start, end, perm) =>
            for (i <- start to end) {
              val tmp = other.get(i)
              other.set(i, other.get(perm(i)))
              other.set(perm(i), tmp)
            }
        }
      }
    }
  }

  implicit class ObjectBindingPimp[A](binding: ObjectBinding[A]) {
    def toProperty: ObjectProperty[A] = new ObjectProperty[A] { this <== binding }
    def toReadOnlyProperty: ReadOnlyObjectProperty[A] = toProperty
  }

  implicit class BooleanBindingPimp(binding: BooleanBinding) {
    def toProperty: BooleanProperty = new BooleanProperty { this <== binding }
    def toReadOnlyProperty: ReadOnlyBooleanProperty = toProperty
  }

  implicit class StringBindingPimp(binding: StringBinding) {
    def toProperty: StringProperty = new StringProperty { this <== binding }
    def toReadOnlyProperty: ReadOnlyStringProperty = toProperty
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

  def createObjectBinding[A, B, C](a: ObservableValue[A], b: ObservableValue[B])
                                  (map: (A, B) => C): ObjectBinding[C] =
    Bindings.createObjectBinding(new Callable[C] {
      override def call() = map(a.getValue, b.getValue)
    }, a, b)
}
