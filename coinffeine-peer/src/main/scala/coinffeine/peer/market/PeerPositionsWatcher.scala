package coinffeine.peer.market

import scala.concurrent.duration.Duration

import akka.actor._

import coinffeine.model.currency.{FiatCurrency, FiatAmount}
import coinffeine.model.market.OrderBookEntry
import coinffeine.model.network.PeerId
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, Offline}
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.messages.brokerage.{Market, PeerPositions, PeerPositionsReceived}

/** A watcher of sending order positions to the broker.
  * 
  * This actor will be created by [[MarketSubmissionActor]] in order to watch the submission of
  * a [[PeerPositions]] message. The watcher will forward  the positions through the message
  * gateway and then it will expect a [[PeerPositionsReceived]] response to notify the requesters
  * appropriately.
  *
  * @param requests  The collection of each order book entry with the actor that requested each order
  * @param gateway   The message gateway to fordward the [[PeerPositions]] message and receive
  *                  the [[PeerPositionsReceived]] message from.
  * @param timeout   The timeout for the watch.
  */
class PeerPositionsWatcher[C <: FiatCurrency](
    market: Market[C],
    requests: Set[(ActorRef, OrderBookEntry[FiatAmount])],
    brokerId: PeerId,
    gateway: ActorRef,
    timeout: Duration) extends Actor with ActorLogging {

  private val peerPositions: PeerPositions[FiatCurrency] =
    PeerPositions(market, requests.map(_._2).toSeq)

  private val nonce = peerPositions.nonce

  log.debug(s"Watching peer positions with nonce $nonce")

  gateway ! MessageGateway.Subscribe {
    case ReceiveMessage(PeerPositionsReceived(_), _) => true
    case _ => false
  }

  gateway ! MessageGateway.ForwardMessage(peerPositions, brokerId)

  context.setReceiveTimeout(timeout)


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
    log.debug(s"Terminating peer positions watcher for nonce $nonce")
    self ! PoisonPill
  }
}

object PeerPositionsWatcher {

  def props[C <: FiatCurrency](
      market: Market[C],
      requests: Set[(ActorRef, OrderBookEntry[FiatAmount])],
      brokerId: PeerId,
      gateway: ActorRef,
      timeout: Duration): Props =
    Props(new PeerPositionsWatcher(market, requests, brokerId, gateway, timeout))
}
