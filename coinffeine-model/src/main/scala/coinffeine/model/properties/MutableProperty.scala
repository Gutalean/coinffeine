package coinffeine.model.properties

import scala.concurrent.ExecutionContext

class MutableProperty[A](initialValue: A) extends Property[A] {

  private case class Listener(handler: OnChangeHandler,
                              executor: ExecutionContext) extends Property.Cancellable {
    def apply(oldValue: A, newValue: A): Unit = {
      if (handler.isDefinedAt(oldValue, newValue)) {
        executor.execute(new Runnable {
          override def run() = handler(oldValue, newValue)
        })
      }
    }

    override def cancel() = {
      MutableProperty.this.synchronized {
        listeners -= this
      }
    }
  }

  private var value: A = initialValue
  private var listeners: Set[Listener] = Set.empty

  override def get: A = value

  override def onChange(handler: OnChangeHandler)
                       (implicit executor: ExecutionContext): Property.Cancellable = synchronized {
    val listener = Listener(handler, executor)
    listeners += listener
    listener
  }

  val readOnly: Property[A] = this

  def set(newValue: A): Unit = synchronized {
    val oldValue = value
    if (oldValue != newValue) {
      value = newValue
      listeners.foreach(_(oldValue, newValue))
    }
  }
}
