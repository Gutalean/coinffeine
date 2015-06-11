package coinffeine.headless.commands

import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.syntax.std.option._
import scalaz.syntax.validation._

import coinffeine.model.order.OrderId
import coinffeine.peer.api.CoinffeineOperations

class OrderIdValidation(operations: CoinffeineOperations) {

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
    operations.orders.get(id).toSuccess("order not found")
}
