package coinffeine.peer.market.orders.archive

import akka.actor.Props

import coinffeine.model.order.{Order, OrderId}

/** Description of the interface of an order archive: an actor able to keep historical records
  * of orders.
  */
object OrderArchive {

  case class ArchiveOrder(order: Order)

  case class OrderArchived(id: OrderId)

  case class CannotArchive(id: OrderId)

  case class Query()

  case class QueryResponse(orders: Seq[Order])

  case class QueryError()

  trait Component {
    def orderArchiveProps: Props
  }
}
