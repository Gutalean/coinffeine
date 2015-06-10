package coinffeine.common.properties

import scala.concurrent.ExecutionContext

trait PropertyMap[K, V] {

  /** A function aimed to process changes on a property entry.
    *
    * It has the form: f(key: K, oldValue: Option[V], newValue: V): Unit
    */
  type OnEntryChangeHandler = (K, Option[V], V) => Unit

  /** A function aimed to process new values on a property entry.
    *
    * It has the form: f(key: K, newValue: V): Unit
    */
  type OnEntryNewValue = (K, V) => Unit

  @throws[NoSuchElementException]
  def apply(key: K): V = get(key).get

  def get(key: K): Option[V]

  def content: Set[(K, V)]

  def keys: Iterable[K] = content.map(_._1)

  def values: Iterable[V] = content.map(_._2)

  /** Set a on-change handler which will be invoked when a property entry changes.
    * It will be invoked immediately for preexisting keys.
    *
    * @param handler    The handler to be invoked when some entry is changed
    * @param executor   The executor used to invoke the handler
    */
  def onChange(handler: OnEntryChangeHandler)
              (implicit executor: ExecutionContext): Cancellable

  /** Set a on-change handler which will be invoked when a new entry value is set
    * It will be invoked immediately for preexisting keys.
    *
    * @param handler    The handler to be invoked when some entry value is set
    * @param executor   The executor used to invoke the handler
    */
  def onNewValue(handler: OnEntryNewValue)
                (implicit executor: ExecutionContext): Cancellable =
    onChange((key, _, value) => handler(key, value))

}
