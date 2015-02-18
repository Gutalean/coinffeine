package coinffeine.peer.market.submission

import akka.actor._
import akka.util.Timeout

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.OrderBookEntry
import coinffeine.model.network.BrokerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.submission.SubmissionSupervisor.{InMarket, Offline}
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
  * @param submission   Information to submit to the order book and who to inform about the result
  * @param gateway      The  message gateway to forward the
  *                     [[PeerPositions]] message and receive the [[PeerPositionsReceived]] message
  *                     from.
  * @param retryPolicy  The time to wait and number of retries to perform
  */
private class PeerPositionsSubmitter[C <: FiatCurrency](
    submission: Submission[C],
    gateway: ActorRef,
    retryPolicy: RetrySettings) extends Actor with ActorLogging {

  private val peerPositions: PeerPositions[C] = submission.toPeerPositions
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
    submission.entries.foreach { entry =>
      entry.requester ! notification(entry.orderBookEntry)
    }
    context.stop(self)
  }
}

object PeerPositionsSubmitter {

  def props[C <: FiatCurrency](submission: Submission[C],
                               gateway: ActorRef,
                               constants: ProtocolConstants): Props = {
    val retryPolicy = RetrySettings(
      Timeout(constants.orderAcknowledgeTimeout), constants.orderAcknowledgeRetries)
    Props(new PeerPositionsSubmitter(submission, gateway, retryPolicy))
  }
}
