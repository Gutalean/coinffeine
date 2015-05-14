package coinffeine.peer.market.orders.archive.h2

import java.io.File
import scala.util.Random

import akka.actor.ActorRef
import org.joda.time.DateTime

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.TempDir
import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.{PeerInfo, Progress}
import coinffeine.model.exchange.ExchangeStatus._
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.model.order._
import coinffeine.model.{ActivityLog, Both}
import coinffeine.peer.market.orders.archive.OrderArchive._

class H2OrderArchiveTest extends AkkaSpec("h2-order-archive") {

  val baseTime = DateTime.now().minusHours(1)
  val cancelledOrderLog = ActivityLog.fromEvents(
    baseTime -> OrderStatus.NotStarted,
    baseTime.plusSeconds(10) -> OrderStatus.InProgress,
    baseTime.plusSeconds(20) -> OrderStatus.Cancelled
  )
  val order1 = ArchivedOrder(
    id = OrderId("order1"),
    orderType = Ask,
    amount = 0.34.BTC,
    price = LimitPrice(100.EUR),
    exchanges = Map.empty,
    log = cancelledOrderLog
  )
  val order2 = ArchivedOrder(
    id = OrderId("order2"),
    orderType = Bid,
    amount = 21000000.BTC,
    price = MarketPrice(Euro),
    exchanges = Map.empty,
    log = cancelledOrderLog
  )
  val exchange = ArchivedExchange(
    id = ExchangeId.random(),
    role = SellerRole,
    exchangedBitcoin = Both(0.33.BTC, 0.34.BTC),
    exchangedFiat = Both(34.4.EUR, 34.EUR),
    counterpartId = PeerId.random(),
    lockTime = 1345L,
    progress = Progress(Both(0.33.BTC, 0.34.BTC)),
    log = ActivityLog.fromEvents(
      baseTime.plusSeconds(15) -> Handshaking,
      baseTime.plusSeconds(20) -> WaitingDepositConfirmation(
        user = PeerInfo("my_account", new KeyPair().publicKey),
        counterpart = PeerInfo("other_account", new KeyPair().publicKey)
      ),
      baseTime.plusMinutes(10) -> Exchanging(Both.fill(randomHash())),
      baseTime.plusMinutes(22) -> Successful
    )
  )

  val order3 = order1.copy(
    id = OrderId("order3"),
    exchanges = Map(exchange.id -> exchange),
    log = ActivityLog.fromEvents(
      baseTime -> OrderStatus.NotStarted,
      baseTime.plusSeconds(10) -> OrderStatus.InProgress,
      baseTime.plusMinutes(15) -> OrderStatus.Completed
    )
  )

  "An H2 order archive" should "start with a clean archive" in withArchive { archive =>
    archive ! Query()
    expectMsg(QueryResponse(Seq.empty))
  }

  it should "reject archiving non-completed orders" in withArchive { archive =>
    archive ! ArchiveOrder(order1.copy(log = ActivityLog.fromEvents(
      baseTime -> OrderStatus.NotStarted,
      baseTime.plusSeconds(10) -> OrderStatus.InProgress
    )))
    expectMsg(CannotArchive(order1.id))
  }

  it should "archive new orders" in withArchive { archive =>
    archive ! ArchiveOrder(order1)
    expectMsg(OrderArchived(order1.id))

    archive ! Query()
    expectMsg(QueryResponse(Seq(order1)))
  }

  it should "list orders sorted by order id" in withArchive { archive =>
    archive ! ArchiveOrder(order1)
    archive ! ArchiveOrder(order2)
    archive ! ArchiveOrder(order3)
    expectMsgAllOf(
      OrderArchived(order1.id),
      OrderArchived(order2.id),
      OrderArchived(order3.id)
    )

    archive ! Query()
    val response = expectMsgType[QueryResponse]
    response.orders should contain allOf (order1, order2, order3)
    response.orders.sortBy(_.id.value) shouldBe response.orders
  }

  private def withArchive(block: ActorRef => Unit): Unit = TempDir.withTempDir { dir =>
    val dbFile = new File(dir, "testDbs")
    val archive = system.actorOf(H2OrderArchive.props(dbFile))
    block(archive)
    system.stop(archive)
  }

  private def randomHash(): Hash = {
    val bytes = new Array[Byte](32)
    Random.nextBytes(bytes)
    new Hash(bytes)
  }
}
