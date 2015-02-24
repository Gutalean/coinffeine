package coinffeine.peer.global

import coinffeine.alarms.Alarm
import coinffeine.model.properties.{MutableProperty, Property}

trait GlobalProperties {
  val alarms: Property[Set[Alarm]]
}

class MutableGlobalProperties extends GlobalProperties {
  override val alarms: MutableProperty[Set[Alarm]] = new MutableProperty[Set[Alarm]](Set.empty)
}

object MutableGlobalProperties {

  trait Component {
    def globalProperties: MutableGlobalProperties
  }
}
