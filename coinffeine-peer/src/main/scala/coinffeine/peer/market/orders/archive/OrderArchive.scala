package coinffeine.peer.market.orders.archive

import akka.actor.Props

import coinffeine.model.order.{AnyCurrencyOrder, OrderId}

/** Description of the interface of an order archive: an actor able to keep historical records
  * of orders.
  */
object OrderArchive {

  case class ArchiveOrder(order: AnyCurrencyOrder)

  case class OrderArchived(id: OrderId)

  case class CannotArchive(id: OrderId)

  case class Query()

  case class QueryResponse(orders: Seq[AnyCurrencyOrder])

  case class QueryError()

  trait Component {
    def orderArchiveProps: Props
  }
}
