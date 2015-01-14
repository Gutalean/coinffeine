package coinffeine.overlay.relay.client

import java.io.IOException
import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.{IO, Tcp}

import coinffeine.overlay.OverlayNetwork

class RelayNetwork(config: ClientConfig, system: ActorSystem) extends OverlayNetwork {
  override val clientProps = ClientActor.props(config, IO(Tcp)(system))
}

object RelayNetwork {
  case class UnexpectedConnectionTermination(cause: String)
    extends IOException(s"Unexpected connection termination: $cause")

  case class CannotStartConnection(address: InetSocketAddress)
    extends IOException(s"Cannot start TCP connection to $address")

  case class InvalidDataReceived(message: String, cause: Throwable = null)
    extends IOException(message, cause)

  case class MessageTooLarge(messageSize: Int, maxPayloadSize: Int)
    extends IOException(s"Cannot send message of size $messageSize, maximum payload of $maxPayloadSize")
}
