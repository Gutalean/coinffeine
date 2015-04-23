package coinffeine.gui.application.operations

import java.util.Comparator

import org.joda.time.DateTime

import coinffeine.gui.application.properties.OrderProperties

/** Compare orders to sort them in reverse creation order  */
class CreationTimestampComparator extends Comparator[OrderProperties] {

  override def compare(left: OrderProperties, right: OrderProperties): Int =
    creationTimestampOf(right).compareTo(creationTimestampOf(left))

  private def creationTimestampOf(right: OrderProperties): DateTime =
    right.orderProperty.get.log.activities.head.timestamp
}
