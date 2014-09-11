package coinffeine.model.properties

import scala.concurrent.ExecutionContext

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
  def onChange(handler: OnChangeHandler)(implicit executor: ExecutionContext): Unit
}
