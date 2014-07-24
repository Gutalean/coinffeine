package coinffeine.protocol.gateway

import akka.actor.Props

import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage

object MessageGateway {

  case class BrokerAddress(hostname: String, port: Int) {
    override def toString = s"$hostname:$port"
  }

  /** A request message to bind & create a new, empty P2P network. */
  case class Bind(listenToPort: Int)

  /** A request message to connect to an already existing network. */
  case class Connect(localPort: Int, connectTo: BrokerAddress)

  /** A response message indicating a success bind operation. */
  case class Bound(ownId: PeerId)

  /** A response message indicating a success connect operation. */
  case class Connected(ownId: PeerId, brokerId: PeerId)

  /** A response message indicating a binding error. */
  case class BindingError(cause: Throwable)

  /** A response message indicating a connection error. */
  case class ConnectingError(cause: Throwable)

  /** A message sent in order to forward another message to a given destination. */
  case class ForwardMessage[M <: PublicMessage](message: M, dest: PeerId)

  type Filter = ReceiveMessage[_ <: PublicMessage] => Boolean

  /** A message sent in order to subscribe for incoming messages.
    *
    * Each actor can only have one active subscription at a time. A second Subscribe message
    * sent to the gateway would overwrite any previous subscription.
    *
    * @param filter A filter function that indicates what messages are forwarded to the sender actor
    */
  case class Subscribe(filter: Filter)

  /** A message sent in order to unsubscribe from incoming message reception. */
  case object Unsubscribe

  /** A message send back to the subscriber. */
  case class ReceiveMessage[M <: PublicMessage](msg: M, sender: PeerId)

  /** An exception thrown when an error is found on message forward. */
  case class ForwardException(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)

  trait Component {
    def messageGatewayProps(ignoredNetworkInterfaces: Seq[String]): Props
  }
}
