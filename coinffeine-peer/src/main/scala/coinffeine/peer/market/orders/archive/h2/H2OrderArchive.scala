package coinffeine.peer.market.orders.archive.h2

import java.io.File
import java.sql.{ResultSet, DriverManager, Timestamp, Types}
import scala.util.{Failure, Success, Try}

import akka.actor.{Actor, ActorLogging, Props}
import org.joda.time.DateTime

import coinffeine.model.{Both, ActivityLog}
import coinffeine.model.currency.{Currency, Bitcoin, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.model.order._
import coinffeine.peer.market.orders.archive.OrderArchive._
import coinffeine.peer.market.orders.archive.h2.serialization.{ExchangeStatusParser, ExchangeStatusFormatter, OrderStatusFormatter, OrderStatusParser}

class H2OrderArchive(dbFile: File) extends Actor with ActorLogging {

  private val conn = {
    Class.forName("org.h2.Driver")
    val conn = DriverManager.getConnection(s"jdbc:h2:$dbFile")
    ArchiveSchema.ensure(conn)
    conn
  }

  override def postStop(): Unit = {
    conn.close()
  }

  override def receive = {
    case ArchiveOrder(order) =>
      val response = archive(order) match {
        case Success(_) =>
          log.debug("Order {} archived", order.id)
          OrderArchived(order.id)
        case Failure(ex) =>
          log.error(ex, "Cannot archive {}: {}", order.id, order)
          CannotArchive(order.id)
      }
      sender() ! response

    case Query() =>
      val response = listOrders() match {
        case Success(orders) => QueryResponse(orders)
        case Failure(ex) =>
          log.error(ex, "Cannot list orders")
          QueryError()
      }
      sender() ! response
  }

  private def archive(order: AnyCurrencyOrder): Try[Unit] = Try {
    require(!order.status.isActive, "Cannot archive active orders")
    insertOrderRecord(order)
    order.exchanges.values.foreach(ex => insertExchange(order.id, ex))
    order.log.activities.foreach(event => insertOrderEvent(order.id, event))
  }

  private def insertOrderRecord(order: AnyCurrencyOrder): Unit = {
    val st = conn.prepareStatement(
      "insert into `order`(id, order_type, amount, price, currency) values (?, ?, ?, ?, ?)")
    st.setString(1, order.id.value)
    st.setString(2, order.orderType.shortName)
    st.setBigDecimal(3, order.amount.value.underlying())
    order.price match {
      case LimitPrice(price) =>
        st.setBigDecimal(4, price.value.underlying())
      case MarketPrice(_) =>
        st.setNull(4, Types.BIGINT)
    }
    st.setString(5, order.price.currency.javaCurrency.getCurrencyCode)
    if (st.executeUpdate() != 1) throw new scala.RuntimeException(s"Cannot insert $order")
  }

  private def insertExchangeEvent(id: ExchangeId,
                                  entry: ActivityLog.Entry[ExchangeStatus]): Unit = {
    val st = conn.prepareStatement(
      "insert into exchange_log(exchange_id, timestamp, event) values (?, ?, ?)")
    st.setString(1, id.value)
    st.setTimestamp(2, new Timestamp(entry.timestamp.getMillis))
    st.setString(3, ExchangeStatusFormatter.format(entry.event))
    if (st.executeUpdate() != 1)
      throw new scala.RuntimeException(s"Cannot insert order event $entry for $id")
  }

  private def insertExchange(orderId: OrderId, exchange: AnyExchange): Unit = {
    insertExchangeRecord(orderId, exchange)
    exchange.log.activities.foreach(event => insertExchangeEvent(exchange.id, event))
  }

  private def insertExchangeRecord(orderId: OrderId, exchange: AnyExchange): Unit = {
    val st = conn.prepareStatement(
      """insert into exchange(
        | id, order_id, role, buyer_bitcoin, seller_bitcoin,
        | buyer_fiat, seller_fiat, counterpart, lock_time, buyer_progress, seller_progress)
        |values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin)
    st.setString(1, exchange.id.value)
    st.setString(2, orderId.value)
    st.setString(3, exchange.role.toString)
    st.setBigDecimal(4, exchange.exchangedBitcoin.buyer.value.underlying())
    st.setBigDecimal(5, exchange.exchangedBitcoin.seller.value.underlying())
    st.setBigDecimal(6, exchange.exchangedFiat.buyer.value.underlying())
    st.setBigDecimal(7, exchange.exchangedFiat.seller.value.underlying())
    st.setString(8, exchange.counterpartId.value)
    st.setLong(9, exchange.lockTime)
    st.setBigDecimal(10, exchange.progress.bitcoinsTransferred.buyer.value.underlying())
    st.setBigDecimal(11, exchange.progress.bitcoinsTransferred.seller.value.underlying())
    if (st.executeUpdate() != 1) throw new scala.RuntimeException(s"Cannot insert $exchange")
  }

  private def insertOrderEvent(id: OrderId, entry: ActivityLog.Entry[OrderStatus]): Unit = {
    val st = conn.prepareStatement(
      "insert into order_log(order_id, timestamp, event) values (?, ?, ?)")
    st.setString(1, id.value)
    st.setTimestamp(2, new Timestamp(entry.timestamp.getMillis))
    st.setString(3, OrderStatusFormatter.format(entry.event))
    if (st.executeUpdate() != 1)
      throw new scala.RuntimeException(s"Cannot insert order event $entry for $id")
  }

  private def listOrders(): Try[Seq[AnyCurrencyOrder]] = Try {
    listOrderIds().map(retrieveOrder)
  }

  private def listOrderIds(): Seq[OrderId] = {
    val st = conn.prepareStatement("select id from `order` order by id")
    toStream(st.executeQuery()) { row =>
      OrderId(row.getString(1))
    }.toList
  }

  private def retrieveOrder(orderId: OrderId): AnyCurrencyOrder = {
    val st = conn.prepareStatement(
      "select order_type, amount, price, currency from `order` where id = ?")
    st.setString(1, orderId.value)
    singleRow(st.executeQuery()) { row =>
      val currency = FiatCurrency(row.getString(4))
      val exchanges = retrieveExchanges[currency.type](orderId, currency)
      ArchivedOrder[currency.type](
        id = orderId,
        orderType = parseOrderType(row.getString(1)),
        amount = Bitcoin.exactAmount(row.getBigDecimal(2)),
        price = parsePrice(Option(row.getBigDecimal(3)), currency),
        exchanges = exchanges.map(ex => ex.id -> ex).toMap,
        log = retrieveOrderLog(orderId)
      )
    }
  }

  private def parseOrderType(orderType: String): OrderType =
    OrderType.parse(orderType).getOrElse(
      throw new scala.RuntimeException(s"unexpected order type $orderType"))

  private def parsePrice[C <: FiatCurrency](decimalOpt: Option[java.math.BigDecimal],
                                            currency: C): OrderPrice[C] =
    decimalOpt.fold[OrderPrice[C]](MarketPrice(currency)) { amount =>
      LimitPrice(CurrencyAmount.exactAmount(amount, currency))
    }

  private def retrieveOrderLog(orderId: OrderId): ActivityLog[OrderStatus] = {
    val st = conn.prepareStatement(
      "select timestamp, event from order_log where order_id = ? order by id")
    st.setString(1, orderId.value)
    toStream(st.executeQuery()) { row =>
      ActivityLog.Entry(
        timestamp = new DateTime(row.getTimestamp(1).getTime),
        event = parseOrderStatus(row.getString(2))
      )
    }.foldLeft(ActivityLog.empty[OrderStatus])(_ record _)
  }

  private def parseOrderStatus(orderStatus: String): OrderStatus =
    OrderStatusParser.parse(orderStatus).getOrElse(
      throw new scala.RuntimeException(s"unexpected order status $orderStatus"))

  private def retrieveExchanges[C <: FiatCurrency](orderId: OrderId,
                                                   currency: C): Seq[ArchivedExchange[C]] =
    listExchangeIds(orderId).map(id => retrieveExchange(id, currency))

  private def listExchangeIds(orderId: OrderId): Seq[ExchangeId] = {
    val st = conn.prepareStatement("select id from exchange where order_id = ? order by id")
    st.setString(1, orderId.value)
    toStream(st.executeQuery()) { row =>
      ExchangeId(row.getString(1))
    }.toList
  }

  private def retrieveExchange[C <: FiatCurrency](exchangeId: ExchangeId,
                                                  currency: C): ArchivedExchange[C] = {
    val st = conn.prepareStatement(
      """select role, buyer_bitcoin, seller_bitcoin, buyer_fiat, seller_fiat, counterpart,
        |  lock_time, buyer_progress, seller_progress
        |from exchange where id = ?""".stripMargin)
    st.setString(1, exchangeId.value)
    singleRow(st.executeQuery()) { row =>
      ArchivedExchange(
        id = exchangeId,
        role = parseRole(row.getString(1)),
        exchangedBitcoin =
          parseBothCurrencyAmounts(Bitcoin, row.getBigDecimal(2), row.getBigDecimal(3)),
        exchangedFiat =
          parseBothCurrencyAmounts(currency, row.getBigDecimal(4), row.getBigDecimal(5)),
        counterpartId = PeerId(row.getString(6)),
        lockTime = row.getLong(7),
        progress = Exchange.Progress(
          parseBothCurrencyAmounts(Bitcoin, row.getBigDecimal(8), row.getBigDecimal(9))),
        log = retrieveExchangeLog(exchangeId)
      )
    }
  }

  private def parseBothCurrencyAmounts[C <: Currency](
      currency: C, buyer: java.math.BigDecimal, seller: java.math.BigDecimal): Both[CurrencyAmount[C]] =
    Both(
      buyer = parseCurrencyAmount(currency, buyer),
      seller = parseCurrencyAmount(currency, seller)
    )

  private def parseCurrencyAmount[C <: Currency](currency: C,
                                                 value: java.math.BigDecimal): CurrencyAmount[C] =
    CurrencyAmount.exactAmount(BigDecimal(value), currency)

  private def parseRole[C <: FiatCurrency](role: String): Role =
    Role.fromString(role).getOrElse(throw new scala.RuntimeException(s"Cannot parse role from $role"))

  private def retrieveExchangeLog(exchangeId: ExchangeId): ActivityLog[ExchangeStatus] = {
    val st = conn.prepareStatement(
      "select timestamp, event from exchange_log where exchange_id = ? order by id")
    st.setString(1, exchangeId.value)
    toStream(st.executeQuery()) { row =>
      ActivityLog.Entry(
        timestamp = new DateTime(row.getTimestamp(1).getTime),
        event = parseExchangeStatus(row.getString(2))
      )
    }.foldLeft(ActivityLog.empty[ExchangeStatus])(_ record _)
  }

  private def parseExchangeStatus(exchangeStatus: String): ExchangeStatus =
    ExchangeStatusParser.parse(exchangeStatus).getOrElse(
      throw new scala.RuntimeException(s"unexpected exchange status $exchangeStatus"))

  private def singleRow[T](rs: ResultSet)(f: ResultSet => T): T = {
    val rows = toStream(rs)(f)
    if (rows.tail.nonEmpty) throw new RuntimeException("Multiple rows but only one was expected")
    rows.headOption.getOrElse(throw new RuntimeException("No results were found, one was expected"))
  }

  private def toStream[T](rs: ResultSet)(f: ResultSet => T): Stream[T] =
    if (rs.next()) f(rs) #:: toStream(rs)(f) else Stream.empty
}

object H2OrderArchive {
  def props(dbFile: File) = Props(new H2OrderArchive(dbFile))
}
