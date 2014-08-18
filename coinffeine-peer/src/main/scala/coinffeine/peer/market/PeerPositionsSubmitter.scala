package coinffeine.peer.market

import scala.concurrent.duration.Duration

import akka.actor._

import coinffeine.common.akka.ServiceRegistry
import coinffeine.model.currency.{FiatCurrency, FiatAmount}
import coinffeine.model.market.OrderBookEntry
import coinffeine.model.network.PeerId
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, Offline}
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.messages.brokerage.{Market, PeerPositions, PeerPositionsReceived}

/** An actor that submits the order positions.
  * 
  * This actor will be created by [[MarketSubmissionActor]] in order to submit and watch  a
  * [[PeerPositions]] message. The watcher will forward  the positions through the message
  * gateway and then it will expect a [[PeerPositionsReceived]] response to notify the requesters
  * appropriately.
  *
  * @param requests  The collection of each order book entry with the actor that requested each order
  * @param registry  The service registry to obtain the message gateway to fordward the
  *                  [[PeerPositions]] message and receive the [[PeerPositionsReceived]] message
  *                  from.
  * @param timeout   The timeout for the watch.
  */
class PeerPositionsSubmitter[C <: FiatCurrency](
    market: Market[C],
    requests: Set[(ActorRef, OrderBookEntry[FiatAmount])],
    brokerId: PeerId,
    registry: ActorRef,
    timeout: Duration) extends Actor with ActorLogging {

  private val peerPositions: PeerPositions[FiatCurrency] =
    PeerPositions(market, requests.map(_._2).toSeq)

  private val nonce = peerPositions.nonce

  override def preStart() = {
    log.debug(s"Submitting and watching peer positions with nonce $nonce")

    implicit val executor = context.dispatcher
    val reg = new ServiceRegistry(registry)
    val gateway = reg.eventuallyLocate(MessageGateway.ServiceId)

    gateway ! MessageGateway.Subscribe {
      case ReceiveMessage(PeerPositionsReceived(_), _) => true
      case _ => false
    }

    gateway ! MessageGateway.ForwardMessage(peerPositions, brokerId)

    context.setReceiveTimeout(timeout)
  }

  override def receive = {
    case ReceiveMessage(PeerPositionsReceived(`nonce`), `brokerId`) =>
      log.debug(s"Peer positions with nonce $nonce successfully received by broker")
      requests.foreach { case (requester, entry) => requester ! InMarket(entry) }
      terminate()
    case ReceiveTimeout =>
      log.error(s"Timeout while watching order positions with nonce $nonce (expected $timeout)")
      requests.foreach { case (requester, entry) => requester ! Offline(entry) }
      terminate()
  }

  private def terminate(): Unit = {
    log.debug(s"Terminating peer positions submitter for nonce $nonce")
    self ! PoisonPill
  }
}

object PeerPositionsSubmitter {

  def props[C <: FiatCurrency](
      market: Market[C],
      requests: Set[(ActorRef, OrderBookEntry[FiatAmount])],
      brokerId: PeerId,
      registry: ActorRef,
      timeout: Duration): Props =
    Props(new PeerPositionsSubmitter(market, requests, brokerId, registry, timeout))
}
