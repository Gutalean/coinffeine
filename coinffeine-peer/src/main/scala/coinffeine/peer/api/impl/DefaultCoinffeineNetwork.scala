package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import org.slf4j.LoggerFactory

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, RetrieveOpenOrders, RetrievedOpenOrders}
import coinffeine.peer.api.CoinffeineNetwork
import coinffeine.peer.api.CoinffeineNetwork._

private[impl] class DefaultCoinffeineNetwork(override val peer: ActorRef)
  extends CoinffeineNetwork with PeerActorWrapper {

  private var _status: CoinffeineNetwork.Status = Disconnected

  override def status = _status

  /** @inheritdoc
    *
    * With the centralized broker implementation over protobuf RPC, "connecting" consists on opening
    * a port with a duplex RPC server.
    */
  override def connect(): Future[Connected.type] = {
    implicit val timeout = Timeout(DefaultCoinffeineNetwork.ConnectionTimeout)
    _status = Connecting
    val bindResult = (peer ? CoinffeinePeerActor.Connect).flatMap {
      case CoinffeinePeerActor.Connected => Future.successful(Connected)
      case CoinffeinePeerActor.ConnectionFailed(cause) => Future.failed(ConnectException(cause))
    }
    bindResult.onComplete {
      case Success(connected) =>
        DefaultCoinffeineNetwork.Log.error("Connected")
        _status = connected
      case Failure(cause) =>
        DefaultCoinffeineNetwork.Log.error("Cannot connect", cause)
        _status = Disconnected
    }
    bindResult
  }

  override def disconnect(): Future[Disconnected.type] = ???

  override def orders: Set[Order[FiatCurrency]] =
    await((peer ? RetrieveOpenOrders).mapTo[RetrievedOpenOrders]).orders.toSet

  override def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C] = {
    peer ! OpenOrder(order)
    order
  }

  override def cancelOrder(order: OrderId): Unit = {
    peer ! CancelOrder(order)
  }
}

object DefaultCoinffeineNetwork {
  val ConnectionTimeout = 30.seconds
  val Log = LoggerFactory.getLogger(classOf[DefaultCoinffeineNetwork])
}
