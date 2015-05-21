package coinffeine.peer.market.orders.archive.h2

import java.math.{BigDecimal => JBigDecimal}

import anorm.SqlParser._
import anorm._
import org.joda.time.DateTime

import coinffeine.model.Both
import coinffeine.model.currency.{Bitcoin, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.Exchange.Progress
import coinffeine.model.exchange.{ExchangeId, ExchangeStatus, Role}
import coinffeine.model.network.PeerId
import coinffeine.model.order._
import coinffeine.peer.market.orders.archive.h2.serialization.{ExchangeStatusParser, OrderStatusParser}

/** Suite of anorm-style parsers able to convert SQL results into domain objects */
private object FieldParsers {

  def both[T](parser: String => RowParser[T], columns: Both[String]): RowParser[Both[T]] = for {
    buyer <- parser(columns.buyer)
    seller <- parser(columns.seller)
  } yield Both(buyer, seller)

  def orderType(name: String): RowParser[OrderType] = str(name).map { orderType =>
    OrderType.parse(orderType).getOrElse(
      throw new scala.RuntimeException(s"unexpected order type $orderType"))
  }

  def role(name: String): RowParser[Role] = str(name).map { role =>
    Role.fromString(role).getOrElse(
      throw new scala.RuntimeException(s"Cannot parse role from $role"))
  }

  def orderId(name: String): RowParser[OrderId] = str(name).map(OrderId.apply)

  def exchangeId(name: String): RowParser[ExchangeId] = str(name).map(ExchangeId.apply)

  def peerId(name: String): RowParser[PeerId] = str(name).map(PeerId.apply)

  def timestamp(name: String): RowParser[DateTime] =
    date(name).map(t => new DateTime(t.getTime))

  def bitcoinAmount(name: String): RowParser[Bitcoin.Amount] =
    get[JBigDecimal](name).map(value => Bitcoin.exactAmount(value))

  def fiatAmount[C <: FiatCurrency](currency: C, name: String): RowParser[CurrencyAmount[C]] =
    get[JBigDecimal](name).map(value => CurrencyAmount.exactAmount(value, currency))

  def progress(names: Both[String]): RowParser[Progress] =
    both(bitcoinAmount, names).map(Progress.apply)

  def price(
      valueColumn: String, currencyColumn: String): RowParser[OrderPrice[FiatCurrency]] = for {
    currencySymbol <- str(currencyColumn)
    currency = FiatCurrency(currencySymbol)
    value <- get[Option[JBigDecimal]](valueColumn)
  } yield value.fold[OrderPrice[FiatCurrency]](MarketPrice(currency)) { amount =>
    LimitPrice(CurrencyAmount.exactAmount(amount, currency))
  }

  def orderStatus(name: String): RowParser[OrderStatus] = str(name).map { status =>
    OrderStatusParser.parse(status).getOrElse(
      throw new scala.RuntimeException(s"unexpected order status $status"))
  }

  def exchangeStatus(name: String): RowParser[ExchangeStatus] = str(name).map { status =>
    ExchangeStatusParser.parse(status).getOrElse(
      throw new scala.RuntimeException(s"unexpected exchange status $status"))
  }
}
