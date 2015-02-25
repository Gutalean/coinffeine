package coinffeine.protocol.gateway

import akka.actor.{ActorSystem, Props}

import coinffeine.alarms.{Severity, Alarm}
import coinffeine.model.network.{BrokerId, NodeId}
import coinffeine.overlay.relay.settings.RelayClientSettings
import coinffeine.protocol.{Version, MessageGatewaySettings}
import coinffeine.protocol.messages.PublicMessage

object MessageGateway {

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

  /** Alarm to be raised if there is a protocol mismatch with the network */
  case class ProtocolMismatchAlarm(ourVersion: Version, networkVersion: Version) extends Alarm {

    override val summary = "Protocol version incompatibility"

    override val whatHappened: String =
      s"""Network protocol version is $networkVersion while this application supports $ourVersion.
         |This means that your orders won't be sent to the market and you should update the
         |application.
       """.stripMargin

    override val howToFix =
      "Go to Coinffeine downloads page and download and install the latest version."

    override val severity = Severity.High
  }

  trait Component {
    def messageGatewayProps(messageGatewaySettings: MessageGatewaySettings,
                            relayClientSettings: RelayClientSettings)
                           (system: ActorSystem): Props
  }
}
