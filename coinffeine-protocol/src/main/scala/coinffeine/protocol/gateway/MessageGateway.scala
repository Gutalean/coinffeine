package coinffeine.protocol.gateway

import java.net.NetworkInterface
import scala.concurrent.duration.FiniteDuration

import akka.actor.{ActorSystem, Props}

import coinffeine.model.network.{BrokerId, NodeId, PeerId}
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.messages.PublicMessage

object MessageGateway {

  sealed trait NodeRole
  case object PeerNode extends NodeRole
  case object BrokerNode extends NodeRole

  case class Join(role: NodeRole, settings: MessageGatewaySettings) {
    val id: PeerId = settings.peerId
  }

  /** A message sent in order to forward a message to a given destination. */
  case class ForwardMessage[M <: PublicMessage](message: M, dest: NodeId)

  type ReceiveFilter = PartialFunction[ReceiveMessage[_ <: PublicMessage], Unit]
  type MessageFilter = PartialFunction[PublicMessage, Unit]

  /** A message sent in order to subscribe for incoming messages.
    *
    * Each actor can only have one active subscription at a time. A second Subscribe message
    * sent to the gateway would overwrite any previous subscription.
    *
    * @param filter A filter function that indicates what messages are forwarded to the sender actor
    */
  case class Subscribe(filter: ReceiveFilter)

  object Subscribe {

    /** Create a [[Subscribe]] message for messages from the broker peer. */
    def fromBroker(filter: MessageFilter): Subscribe = Subscribe {
      case ReceiveMessage(msg, BrokerId) if filter.isDefinedAt(msg) =>
    }
  }

  /** A message sent in order to unsubscribe from incoming message reception. */
  case object Unsubscribe

  /** A message send back to the subscriber. */
  case class ReceiveMessage[M <: PublicMessage](msg: M, sender: NodeId)

  /** An exception thrown when an error is found on message forward. */
  case class ForwardException(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)

  trait Component {

    def messageGatewayProps(settings: MessageGatewaySettings)(system: ActorSystem): Props =
      messageGatewayProps(settings.ignoredNetworkInterfaces, settings.connectionRetryInterval)(system)

    def messageGatewayProps(ignoredNetworkInterfaces: Seq[NetworkInterface],
                            connectionRetryInterval: FiniteDuration)(system: ActorSystem): Props
  }
}
