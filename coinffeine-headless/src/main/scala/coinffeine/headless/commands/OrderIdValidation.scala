package coinffeine.headless.commands

import scalaz.{Scalaz, Validation}

import coinffeine.model.market.OrderId
import coinffeine.peer.api.CoinffeineNetwork

class OrderIdValidation(network: CoinffeineNetwork) {
  import Scalaz._

  def requireExistingOrderId(text: String): Validation[String, OrderId] = for {
    _ <- requireNonEmpty(text)
    id <- requireOrderId(text)
    _ <- requireExistingOrder(id)
  } yield id

  private def requireNonEmpty(args: String) =
    if (args.nonEmpty) ().success else "missing order id".failure

  private def requireOrderId(text: String) =
    OrderId.parse(text).toSuccess(s"invalid order id: '$text'")

  private def requireExistingOrder(id: OrderId) =
    network.orders.get(id).toSuccess("order not found")
}
