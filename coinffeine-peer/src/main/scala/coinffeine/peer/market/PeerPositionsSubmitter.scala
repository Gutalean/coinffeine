package coinffeine.peer.market

import akka.actor._
import akka.util.Timeout

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Market, OrderBookEntry}
import coinffeine.model.network.BrokerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, Offline}
import coinffeine.protocol.gateway.MessageForwarder
import coinffeine.protocol.gateway.MessageForwarder.RetrySettings
import coinffeine.protocol.messages.brokerage.{PeerPositions, PeerPositionsReceived}

/** An actor that submits the order positions.
  *
  * This actor will be created by [[MarketSubmissionActor]] in order to submit and watch  a
  * [[PeerPositions]] message. The watcher will forward  the positions through the message
  * gateway and then it will expect a [[PeerPositionsReceived]] response to notify the requesters
  * appropriately.
  *
  * @param market       Market to submit orders to
  * @param requests     The collection of each order book entry with the actor that requested each order
  * @param gateway      The  message gateway to forward the
  *                     [[PeerPositions]] message and receive the [[PeerPositionsReceived]] message
  *                     from.
  * @param retryPolicy  The time to wait and number of retries to perform
  */
private class PeerPositionsSubmitter[C <: FiatCurrency](
    market: Market[C],
    requests: Set[(ActorRef, OrderBookEntry[C])],
    gateway: ActorRef,
    retryPolicy: RetrySettings) extends Actor with ActorLogging {

  private val peerPositions: PeerPositions[C] =
    PeerPositions(market, requests.map(_._2).toSeq)

  private val nonce = peerPositions.nonce

  override def preStart() = {
    log.debug("Submitting and watching peer positions with nonce {}", nonce)
    MessageForwarder.Factory(gateway).forward(peerPositions, BrokerId, retryPolicy) {
      case confirmation @ PeerPositionsReceived(`nonce`) => Status.Success
    }
  }

  override def receive = {
    case Status.Success =>
      log.debug("Peer positions with nonce {} successfully received by broker", nonce)
      terminate(InMarket.apply)


    case MessageForwarder.ConfirmationFailed(_) =>
      log.error("Timeout while watching order positions with nonce {} ({})", nonce, retryPolicy)
      terminate(Offline.apply)
  }

  private def terminate(notification: OrderBookEntry[_ <: FiatCurrency] => Any): Unit = {
    requests.foreach { case (requester, entry) => requester ! notification(entry) }
    context.stop(self)
  }
}

object PeerPositionsSubmitter {

  def props[C <: FiatCurrency](
      market: Market[C],
      requests: Set[(ActorRef, OrderBookEntry[C])],
      gateway: ActorRef,
      constants: ProtocolConstants): Props = {
    val retryPolicy = RetrySettings(
      Timeout(constants.orderAcknowledgeTimeout), constants.orderAcknowledgeRetries)
    Props(new PeerPositionsSubmitter(market, requests, gateway, retryPolicy))
  }
}
