package coinffeine.model.properties

import scala.concurrent.ExecutionContext

class MutablePropertyMap[K, V] extends PropertyMap[K, V] {

  private val listeners = new PropertyListeners[OnEntryChangeHandler]
  private var map: Map[K, V] = Map.empty

  override def get(key: K): Option[V] = synchronized {
    map.get(key)
  }

  override def values: Iterable[V] = synchronized { map.values }

  override def onChange(handler: OnEntryChangeHandler)(implicit executor: ExecutionContext) = {
    listeners.add(handler)
  }

  def set(key: K, value: V): Unit = synchronized {
    val prevValue = map.get(key)
    if (prevValue != Some(value)) {
      map += key -> value
      listeners.invoke(handler => handler(key, prevValue, value))
    }
  }
}
