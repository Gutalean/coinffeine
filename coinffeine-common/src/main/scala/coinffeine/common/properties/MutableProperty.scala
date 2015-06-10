package coinffeine.common.properties

import scala.concurrent.ExecutionContext

class MutableProperty[A](initialValue: A) extends Property[A] {

  private val listeners = new PropertyListeners[OnChangeHandler]
  private var value: A = initialValue

  override def get: A = value

  override def onChange(handler: OnChangeHandler)
                       (implicit executor: ExecutionContext) = {
    handler(value, value)
    listeners.add(handler)
  }

  val readOnly: Property[A] = this

  def set(newValue: A): Unit = update(_ => newValue)

  def update(f: A => A): Unit = synchronized {
    val oldValue = value
    val newValue = f(oldValue)
    if (oldValue != newValue) {
      value = newValue
      listeners.invoke(handler => handler(oldValue, newValue))
    }
  }
}
