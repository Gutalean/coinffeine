package coinffeine.model.properties

import scala.concurrent.ExecutionContext

class MutableProperty[A](initialValue: A) extends Property[A] {

  private case class Listener(handler: OnChangeHandler, executor: ExecutionContext) {
    def apply(oldValue: A, newValue: A): Unit = {
      if (handler.isDefinedAt(oldValue, newValue)) {
        executor.execute(new Runnable {
          override def run() = handler(oldValue, newValue)
        })
      }
    }
  }

  private var value: A = initialValue
  private var listeners: Set[Listener] = Set.empty

  override def get: A = value

  override def onChange(handler: OnChangeHandler)
                       (implicit executor: ExecutionContext): Unit = synchronized {
    listeners += Listener(handler, executor)
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
