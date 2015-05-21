package coinffeine.peer.market.orders.archive.h2

import java.io.File
import java.sql.{DriverManager, ResultSet, Timestamp}
import scala.util.{Failure, Success, Try}

import akka.actor.{Actor, ActorLogging, Props}
import anorm.SqlParser._
import anorm._

import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.order._
import coinffeine.model.{ActivityLog, Both}
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.market.orders.archive.OrderArchive
import coinffeine.peer.market.orders.archive.OrderArchive._
import coinffeine.peer.market.orders.archive.h2.serialization._
import coinffeine.peer.market.orders.archive.h2.{FieldParsers => p}

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
    val query = SQL(
      """insert into `order`(id, order_type, amount, price, currency)
        |values ({id}, {order_type}, {amount}, {price}, {currency})""".stripMargin
    ).on(
      "id" -> order.id.value,
      "order_type" -> order.orderType.shortName,
      "amount" -> order.amount.value,
      "price" -> order.price.toOption.map(_.value),
      "currency" -> order.price.currency.javaCurrency.getCurrencyCode
    )
    if (query.executeUpdate()(conn) != 1)
      throw new scala.RuntimeException(s"Cannot insert $order")
  }

  private def insertExchangeEvent(id: ExchangeId,
                                  entry: ActivityLog.Entry[ExchangeStatus]): Unit = {
    val query = SQL(
      """insert into exchange_log(owner_id, timestamp, event)
        |values ({owner_id}, {timestamp}, {event})""".stripMargin
    ).on(
      "owner_id" -> id.value,
      "timestamp" -> new Timestamp(entry.timestamp.getMillis),
      "event" -> ExchangeStatusFormatter.format(entry.event)
    )
    if (query.executeUpdate()(conn) != 1)
      throw new scala.RuntimeException(s"Cannot insert order event $entry for $id")
  }

  private def insertExchange(orderId: OrderId, exchange: AnyExchange): Unit = {
    insertExchangeRecord(orderId, exchange)
    exchange.log.activities.foreach(event => insertExchangeEvent(exchange.id, event))
  }

  private def insertExchangeRecord(orderId: OrderId, exchange: AnyExchange): Unit = {
    val query = SQL(
      """insert into exchange(
        |  id, order_id, role, buyer_bitcoin, seller_bitcoin,
        |  buyer_fiat, seller_fiat, counterpart, lock_time, buyer_progress, seller_progress)
        |values ({id}, {order_id}, {role}, {buyer_bitcoin}, {seller_bitcoin},
        |  {buyer_fiat}, {seller_fiat}, {counterpart}, {lock_time}, {buyer_progress},
        |  {seller_progress})""".stripMargin
    ).on(
      "id" -> exchange.id.value,
      "order_id" -> orderId.value,
      "role" -> exchange.role.toString,
      "buyer_bitcoin" -> exchange.exchangedBitcoin.buyer.value,
      "seller_bitcoin" -> exchange.exchangedBitcoin.seller.value,
      "buyer_fiat" -> exchange.exchangedFiat.buyer.value,
      "seller_fiat" -> exchange.exchangedFiat.seller.value,
      "counterpart" -> exchange.counterpartId.value,
      "lock_time" -> exchange.lockTime,
      "buyer_progress" -> exchange.progress.bitcoinsTransferred.buyer.value,
      "seller_progress" -> exchange.progress.bitcoinsTransferred.seller.value
    )
    if (query.executeUpdate()(conn) != 1)
      throw new scala.RuntimeException(s"Cannot insert $exchange")
  }

  private def insertOrderEvent(id: OrderId, entry: ActivityLog.Entry[OrderStatus]): Unit = {
    val query = SQL(
      """insert into order_log(owner_id, timestamp, event)
        |values ({owner_id}, {timestamp}, {event})""".stripMargin
    ).on(
      "owner_id" -> id.value,
      "timestamp" -> new Timestamp(entry.timestamp.getMillis),
      "event" -> OrderStatusFormatter.format(entry.event)
    )
    if (query.executeUpdate()(conn) != 1)
      throw new scala.RuntimeException(s"Cannot insert order event $entry for $id")
  }

  private def listOrders(): Try[Seq[AnyCurrencyOrder]] = Try {
    listOrderIds().flatMap(retrieveOrder)
  }

  private def listOrderIds(): Seq[OrderId] =
    SQL("select id from `order` order by id").as(p.orderId("id") *)(conn).toSeq

  private def retrieveOrder(orderId: OrderId): Option[AnyCurrencyOrder] = {
    val orderParser =
      p.orderType("order_type") ~ p.bitcoinAmount("amount") ~ p.price("price", "currency") map {
        case orderType ~ amount ~ price =>
          ArchivedOrder(
            id = orderId,
            orderType = orderType,
            amount = amount,
            price = price,
            exchanges = retrieveExchanges(orderId, price.currency),
            log = retrieveOrderLog(orderId)
          )
    }
    SQL("select order_type, amount, price, currency from `order` where id = {id}")
      .on("id" -> orderId.value)
      .as(orderParser.singleOpt)(conn)
  }

  private def retrieveOrderLog(orderId: OrderId): ActivityLog[OrderStatus] =
    retrieveActivityLog(orderId.value, "order_log", p.orderStatus)

  private def retrieveExchanges[C <: FiatCurrency](
      orderId: OrderId, currency: C): Map[ExchangeId, ArchivedExchange[C]] = (for {
    id <- listExchangeIds(orderId)
    exchange <- retrieveExchange(id, currency)
  } yield id -> exchange).toMap

  private def listExchangeIds(orderId: OrderId): Seq[ExchangeId] =
    SQL("select id from exchange where order_id = {order_id} order by id")
      .on("order_id" -> orderId.value)
      .as(p.exchangeId("id") *)(conn)
      .toSeq

  private def retrieveExchange[C <: FiatCurrency](
      exchangeId: ExchangeId, currency: C): Option[ArchivedExchange[C]] =
    SQL("""select role, buyer_bitcoin, seller_bitcoin, buyer_fiat, seller_fiat, counterpart,
          |  lock_time, buyer_progress, seller_progress
          |  from exchange where id = {id}""".stripMargin)
      .on("id" -> exchangeId.value)
      .as(p.role("role") ~
        p.both(p.bitcoinAmount, Both("buyer_bitcoin", "seller_bitcoin")) ~
        p.both(p.fiatAmount(currency, _), Both("buyer_fiat", "seller_fiat")) ~
        p.peerId("counterpart") ~
        long("lock_time") ~
        p.progress(Both("buyer_progress", "seller_progress")) singleOpt)(conn)
      .map { case role ~ exchangedBitcoin ~ exchangedFiat ~ counterpartId ~ lockTime ~ progress =>
        ArchivedExchange(
          exchangeId,
          role,
          exchangedBitcoin,
          exchangedFiat,
          counterpartId,
          lockTime,
          retrieveExchangeLog(exchangeId),
          progress
        )
      }

  private def retrieveExchangeLog(exchangeId: ExchangeId): ActivityLog[ExchangeStatus] =
    retrieveActivityLog(exchangeId.value, "exchange_log", p.exchangeStatus)

  private def retrieveActivityLog[T](
    id: String, table: String, eventParser: String => RowParser[T]): ActivityLog[T] =
    SQL(s"select timestamp, event from $table where owner_id = {id} order by id")
      .on("id" -> id)
      .as(p.timestamp("timestamp") ~ eventParser("event") *)(conn)
      .foldLeft(ActivityLog.empty[T]) { case (log, timestamp ~ event) =>
      log.record(event, timestamp)
    }
}

object H2OrderArchive {
  val DefaultFilename = "archive"

  def props(dbFile: File) = Props(new H2OrderArchive(dbFile))

  trait Component extends OrderArchive.Component { this: ConfigComponent =>

    override def orderArchiveProps: Props = props(new File(configProvider.dataPath, DefaultFilename))
  }
}
