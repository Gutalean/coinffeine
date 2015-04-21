package coinffeine.gui.application.operations

import java.util.Comparator

import coinffeine.gui.application.properties.OrderProperties

/** Compare orders to sort them from most recent changed to most distant ones  */
class LastChangeComparator extends Comparator[OrderProperties] {

  override def compare(left: OrderProperties, right: OrderProperties): Int =
    right.orderProperty.get.lastChange.compareTo(left.orderProperty.get.lastChange)
}
