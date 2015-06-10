package coinffeine.gui.notification

import coinffeine.common.properties.PropertyMap

class PropertyMapNotification[K, V](property: PropertyMap[K, V],
                                    mapping: PropertyMapNotification.Mapping[K, V]) {

  import coinffeine.gui.util.FxExecutor.asContext

  private val cancellation = property.onChange { (id, oldVal, newVal) =>
    mapping(id, oldVal, newVal).foreach(_.showNotification())
  }

  def cancel(): Unit = {
    cancellation.cancel()
  }
}

object PropertyMapNotification {

  type Mapping[K, V] = (K, Option[V], V) => Option[Event]

  def apply[K, V](property: PropertyMap[K, V])
                 (mapping: PropertyMapNotification.Mapping[K, V]) =
    new PropertyMapNotification(property, mapping)
}
