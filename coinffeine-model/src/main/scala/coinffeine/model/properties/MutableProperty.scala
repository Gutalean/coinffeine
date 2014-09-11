package coinffeine.model.properties

import scala.concurrent.ExecutionContext

class MutableProperty[A](initialValue: A) extends Property[A] {

  case class HandlerInfo(handler: OnChangeHandler, executor: ExecutionContext)

  private var value: A = initialValue
  private var handlers: Set[HandlerInfo] = Set.empty

  override def get: A = value

  override def onChange(handler: OnChangeHandler)
                       (implicit executor: ExecutionContext): Unit = synchronized {
    handlers += HandlerInfo(handler, executor)
  }

  val readOnly: Property[A] = this

  def set(newValue: A): Unit = synchronized {
    val oldValue = value
    if (oldValue != newValue) {
      value = newValue
      handlers.foreach { handlerInfo =>
        if (handlerInfo.handler.isDefinedAt(oldValue, newValue)) {
          handlerInfo.executor.execute(new Runnable {
            override def run() = handlerInfo.handler(oldValue, newValue)
          })
        }
      }
    }
  }
}
