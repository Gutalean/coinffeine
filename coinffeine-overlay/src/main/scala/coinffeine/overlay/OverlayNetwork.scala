package coinffeine.overlay

import java.io.IOException

import akka.actor.Props
import akka.util.ByteString

trait OverlayNetwork {
  type Config

  /** Produce the props of an actor able to join the overlay network this trait represents. */
  def clientProps(config: Config): Props
}

object OverlayNetwork {

  /** Message to the overlay network client to try to join the overlay network.
    * The sender ref will be regarded as the "listener" and will receive either a
    * [[Joined]] or a [[JoinFailed]] message.
    *
    * @param id  Id to use within the overlay
    */
  case class Join(id: OverlayId)

  /** Response sent by the network client to signal that we have joined the overlay and we can now
    * send and receive messages. */
  case class Joined(id: OverlayId)

  /** Response sent by the network client when a join attempt has failed */
  case class JoinFailed(id: OverlayId, cause: JoinFailureCause)

  /** Instruct the network client to send a message to other node. */
  case class SendMessage(target: OverlayId, message: ByteString)

  /** Message sent to the listener when a message can't be sent */
  case class CannotSend(request: SendMessage, cause: CannotSendCause)

  /** Sent by the network client to its listener whenever a new message is received. */
  case class ReceiveMessage(source: OverlayId, message: ByteString)

  /** Either if the network client receives this message or the listener actor dies, the client
    * will start its disconnection from the overlay network. */
  case object Leave

  /** Notification of having leaved the overlay network. It is send after a requested disconnection
    * or after an involuntary disconnection because of the underlying network availability. */
  case class Leaved(id: OverlayId, cause: LeaveCause)

  sealed trait JoinFailureCause
  case object AlreadyJoining extends JoinFailureCause
  case object AlreadyJoined extends JoinFailureCause
  case class UnderlyingNetworkFailure(error: IOException) extends JoinFailureCause

  sealed trait CannotSendCause
  case object UnavailableNetwork extends CannotSendCause

  sealed trait LeaveCause
  case object RequestedLeave extends LeaveCause
  case class UnexpectedLeave(error: Throwable) extends LeaveCause
}
