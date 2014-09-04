package coinffeine.protocol.gateway

import java.net.NetworkInterface

import akka.actor.Props

import coinffeine.common.akka.ServiceRegistryActor
import coinffeine.model.network.{BrokerId, NodeId}
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.messages.PublicMessage

object MessageGateway {

  /** The identifier of the message gateway as global service. */
  val ServiceId = ServiceRegistryActor.ServiceId("/Coinffeine/MessageGateway")

  /** A request message to get the connection status as a
    * [[coinffeine.model.event.CoinffeineConnectionStatus]].
    */
  case object RetrieveConnectionStatus

  case class BrokerAddress(hostname: String, port: Int) {
    override def toString = s"$hostname:$port"
  }

  sealed trait Join

  /** A request message to bind & create a new, empty P2P network. */
  case class JoinAsBroker(localPort: Int) extends Join

  /** A request message to join to an already existing network. */
  case class JoinAsPeer(localPort: Int, connectTo: BrokerAddress) extends Join

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

    def messageGatewayProps(settings: MessageGatewaySettings): Props =
      messageGatewayProps(settings.ignoredNetworkInterfaces)

    def messageGatewayProps(ignoredNetworkInterfaces: Seq[NetworkInterface]): Props
  }
}
