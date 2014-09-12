package coinffeine.model.properties

import scala.concurrent.{Promise, Future, ExecutionContext}

/** A property that encapsulates some meaningful data in Coinffeine. */
trait Property[A] {

  /** A partial function aimed to process changes on a property.
    *
    * It has the form: f(oldValue: A, newValue: A): Unit
    */
  type OnChangeHandler = PartialFunction[(A, A), Unit]

  /** Retrieve the current value of the property. */
  def get: A

  /** Set a on-change handler which will be invoked when the property value changes.
    *
    * @param handler    The handler to be invoked when the value of the property changes
    * @param executor   The executor used to invoke the handler
    */
  def onChange(handler: OnChangeHandler)(implicit executor: ExecutionContext): Property.Cancellable

  /** Register a handler for a event on this property.
    *
    * This function would register a on-change handler that will set a success in a future
    * when the given function matches the value of the property. Then, the handler is unregistered.
    * This may be used to observe for a determined change in the property and obtain a future
    * that represents such a change.
    *
    * @param f          The function that determines whether the condition is met.
    * @param executor   The execution context the handler will be executed.
    * @return           The future representing the result of the property value matching.
    */
  def when[B](f: PartialFunction[A, B])(implicit executor: ExecutionContext): Future[B] = {
    val p = Promise[B]()
    val currentValue = get
    if (f.isDefinedAt(currentValue)) {
      p.success(f(currentValue))
    } else {
      val handler = onChange {
        case (_, newValue) if !p.isCompleted && f.isDefinedAt(newValue) => p.success(f(newValue))
      }
      p.future.onComplete(_ => handler.cancel())
    }
    p.future
  }
}

object Property {

  trait Cancellable {
    def cancel(): Unit
  }
}