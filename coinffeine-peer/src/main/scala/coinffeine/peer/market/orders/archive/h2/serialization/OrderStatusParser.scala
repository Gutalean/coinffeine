package coinffeine.peer.market.orders.archive.h2.serialization

import coinffeine.model.order._

private[h2] object OrderStatusParser {

  private object Parser extends Parsers[OrderStatus] {

    override val mainParser =
      OrderStatus.NotStarted.parser |
      OrderStatus.InProgress.parser |
      OrderStatus.Completed.parser |
      OrderStatus.Cancelled.parser
  }

  def parse(input: String): Option[OrderStatus] = Parser.parse(input)
}
