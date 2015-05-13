package coinffeine.peer.market.orders.archive.h2.serialization

import scala.util.parsing.combinator.JavaTokenParsers

private[h2] trait Parsers[T] extends JavaTokenParsers {

  implicit class ObjectPimp[O <: Product with Singleton](obj: O) {
    def parser: Parser[O] = obj.productPrefix ^^ { _ => obj }
  }

  def mainParser: Parser[T]

  def parse(input: String): Option[T] = parseAll(mainParser, input).map(Some.apply).getOrElse(None)
}
