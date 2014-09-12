package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.pattern._
import org.slf4j.LoggerFactory

import coinffeine.common.akka.AskPattern
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Order, OrderId}
import coinffeine.model.network.{PeerId, CoinffeineNetworkProperties}
import coinffeine.model.properties.Property
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, RetrieveOpenOrders, RetrievedOpenOrders}
import coinffeine.peer.api.CoinffeineNetwork
import coinffeine.peer.api.CoinffeineNetwork._

private[impl] class DefaultCoinffeineNetwork(
    properties: CoinffeineNetworkProperties,
    override val peer: ActorRef) extends CoinffeineNetwork with PeerActorWrapper {

  override val activePeers: Property[Int] = properties.activePeers
  override val brokerId: Property[Option[PeerId]] = properties.brokerId

  override def orders = await((peer ? RetrieveOpenOrders).mapTo[RetrievedOpenOrders]).orders.toSet

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
