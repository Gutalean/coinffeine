package coinffeine.overlay.relay.client

import java.io.IOException
import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}

import coinffeine.overlay.OverlayNetwork

class RelayNetwork private (system: ActorSystem) extends OverlayNetwork {
  override type Config = ClientConfig

  override def clientProps(config: Config): Props = ???
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
