package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.pattern._
import org.slf4j.LoggerFactory

import coinffeine.common.akka.AskPattern
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, RetrieveOpenOrders, RetrievedOpenOrders}
import coinffeine.peer.api.CoinffeineNetwork
import coinffeine.peer.api.CoinffeineNetwork._

private[impl] class DefaultCoinffeineNetwork(override val peer: ActorRef)
  extends CoinffeineNetwork with PeerActorWrapper {

  override def status = await(AskPattern(
    to = peer,
    request = CoinffeinePeerActor.RetrieveConnectionStatus,
    errorMessage = "Cannot get connection status"
  ).withImmediateReply[CoinffeinePeerActor.ConnectionStatus]().map { status =>
    if (status.connected) Connected else Disconnected
  })

  override def orders: Set[Order[FiatCurrency]] =
    await((peer ? RetrieveOpenOrders).mapTo[RetrievedOpenOrders]).orders.toSet

  override def submitOrder[C <: FiatCurrency](order: Order[C]): Order[C] = {
    peer ! OpenOrder(order)
    order
  }

  override def cancelOrder(order: OrderId, reason: String): Unit = {
    peer ! CancelOrder(order, reason)
  }
}

object DefaultCoinffeineNetwork {
  val ConnectionTimeout = 30.seconds
  val Log = LoggerFactory.getLogger(classOf[DefaultCoinffeineNetwork])
}
