package com.coinffeine.client.app

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import akka.actor.ActorRef
import akka.pattern._

import com.coinffeine.client.api.{CoinffeineNetwork, Exchange}
import com.coinffeine.client.api.CoinffeineNetwork._
import com.coinffeine.client.peer.CoinffeinePeerActor
import com.coinffeine.client.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, RetrieveOpenOrders, RetrievedOpenOrders}
import com.coinffeine.common.Order

private[app] class DefaultCoinffeineNetwork(override val peer: ActorRef)
  extends CoinffeineNetwork with PeerActorWrapper {

  private var _status: CoinffeineNetwork.Status = Disconnected

  override def status = _status

  /** @inheritdoc
    *
    * With the centralized broker implementation over protobuf RPC, "connecting" consists on opening
    * a port with a duplex RPC server.
    */
  override def connect(): Future[Connected.type] = {
    _status = Connecting
    val bindResult = (peer ? CoinffeinePeerActor.Connect).flatMap {
      case CoinffeinePeerActor.Connected => Future.successful(Connected)
      case CoinffeinePeerActor.ConnectionFailed(cause) => Future.failed(ConnectException(cause))
    }
    bindResult.onComplete {
      case Success(connected) => _status = connected
      case Failure(_) => _status = Disconnected
    }
    bindResult
  }

  override def disconnect(): Future[Disconnected.type] = ???

  override def exchanges: Set[Exchange] = Set.empty

  override def onExchangeChanged(listener: ExchangeListener): Unit = ???

  override def orders: Set[Order] =
    Await.result((peer ? RetrieveOpenOrders).mapTo[RetrievedOpenOrders].map(_.orders), timeout.duration)

  override def submitOrder(order: Order): Order = {
    peer ! OpenOrder(order)
    order
  }

  override def cancelOrder(order: Order): Unit = {
    peer ! CancelOrder(order)
  }
}
