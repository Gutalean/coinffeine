package coinffeine.gui.beans

import java.util.concurrent.Callable
import javafx.beans.binding.{Bindings, ObjectBinding}
import javafx.beans.{InvalidationListener, Observable}
import coinffeine.common.properties.PropertyMap
import coinffeine.gui.util.FxExecutor._

class PropertyMapPimp[K, V](property: PropertyMap[K, V]) extends Observable {

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
