package coinffeine.gui.beans

import coinffeine.model.properties.Cancellable

private[beans] class CancellableListeners[L] {

  private var listeners: Map[L, Cancellable] = Map.empty

  def add(listener: L, cancellable: Cancellable): Unit = {
    listeners += listener -> cancellable
  }

  def cancel(listener: L): Unit = {
    listeners.get(listener).foreach(_.cancel())
    listeners -= listener
  }
}
