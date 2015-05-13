package coinffeine.peer.market.orders.archive.h2.serialization

import scala.util.parsing.combinator.JavaTokenParsers

import coinffeine.model.order._

private[h2] object OrderStatusParser {

  private object Parser extends JavaTokenParsers {

    implicit class ObjectPimp[T <: Product with Singleton](val obj: T) extends AnyVal {
      def parser: Parser[T] = obj.productPrefix ^^ { _ => obj }
    }

    val orderStatusParser: Parser[OrderStatus] =
      OrderStatus.NotStarted.parser |
      OrderStatus.InProgress.parser |
      OrderStatus.Completed.parser |
      OrderStatus.Cancelled.parser
  }

  def parse(input: String): Option[OrderStatus] =
    Parser.parseAll(Parser.orderStatusParser, input)
      .map(Some.apply)
      .getOrElse(None)
}
