package coinffeine.protocol.gateway

import scala.concurrent.duration._

import akka.actor._
import akka.util.Timeout

import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage

/** A message forwarder actor.
  *
  * This actor can be use to forward a message to the Coinffeine network and expect a
  * response from the receiver. If no response is received in the given timeout, the
  * message is forwarded again up to a maximum number of retries.
  *
  * The message is considered as received once a partial function passed in the forward
  * request matches one of the messages received by the message gateway. This very function
  * is used to extract a value from the response that will be sent to the actor who requested
  * the original message forwarding.
  *
  * Each message forwarder can be used only once. When the forwarding process is completed,
  * no further action is done.
  *
  * @param messageGateway The actor that implements the message gateway logic
  */
class MessageForwarder(messageGateway: ActorRef) extends Actor with ActorLogging {

  import MessageForwarder._
  import MessageGateway._

  override val receive = waitForForward

  private def waitForForward: Receive = {
    case Forward(msg, to, confirmation, timeout, retries) =>
      subscribeTo(confirmation, to)
      forwardTo(msg, to)
      setReceptionTimeout(timeout)
      context.become(waitForConfirmation(msg, confirmation, to, sender(), retries))
  }

  private def subscribeTo[A](confirmation: PartialFunction[PublicMessage, A],
                             from: PeerId): Unit = {
    messageGateway ! Subscribe {
      case ReceiveMessage(msg, `from`) if confirmation.isDefinedAt(msg) =>
    }
  }

  private def forwardTo(msg: PublicMessage, to: PeerId): Unit = {
    messageGateway ! ForwardMessage(msg, to)
  }

  private def setReceptionTimeout(timeout: Timeout): Unit = {
    context.setReceiveTimeout(timeout.duration)
  }

  private def cancelReceptionTimeout(): Unit = {
    context.setReceiveTimeout(Duration.Undefined)
  }

  private def terminate(): Unit = {
    messageGateway ! Unsubscribe
    cancelReceptionTimeout()
    self ! PoisonPill

    // This prevents a possibly queued timeout to be processed
    context.become { case _ => }
  }

  private def waitForConfirmation[A](
      msg: PublicMessage,
      confirmation: PartialFunction[PublicMessage, A],
      destination: PeerId,
      requester: ActorRef,
      remainingRetries: Int): Receive = {
    case ReceiveMessage(response, `destination`) if confirmation.isDefinedAt(response) =>
      requester ! confirmation(response)
      terminate()
    case ReceiveTimeout if remainingRetries > 0 =>
      forwardTo(msg, destination)
      context.become(
        waitForConfirmation(msg, confirmation, destination, requester, remainingRetries - 1))
    case ReceiveTimeout =>
      requester ! ConfirmationFailed(msg)
      terminate()
  }
}

object MessageForwarder {

  val DefaultTimeout = Timeout(5.seconds)
  val DefaultMaxRetries = 3

  // TODO: don't pass the request in a actor message but to its constructor
  /** A request to forward the given message.
    *
    * This message is sent to the message forwarder in order to request it to forward a message.
    * When received, it will send the message using the gateway and will wait for a confirmation.
    * If the confirmation is not received, it will resend the message up to a number of retries.
    * If the message is finally received, the resulting confirmation is sent to the requester actor.
    * If the message is not confirmed, a [[ConfirmationFailed]] message is sent.
    *
    * @param msg              The message to be forwarded
    * @param to               The destination of the message
    * @param maxRetries  The maximum number of times the message will be retries to be
    *                         forwarded
    * @param confirmation     A partial function that determines an incoming message that confirms
    *                         the forwarded one.
    */
  case class Forward[A](msg: PublicMessage,
                        to: PeerId,
                        confirmation: PartialFunction[PublicMessage, A],
                        timeout: Timeout,
                        maxRetries: Int) {

    require(maxRetries >= 0, "number of retries must not be negative")
  }

  object Forward {

    def apply[A](msg: PublicMessage, to: PeerId,
                 timeout: Timeout = DefaultTimeout, maxRetries: Int = DefaultMaxRetries)
                (confirmation: PartialFunction[PublicMessage, A]): Forward[A] =
      apply(msg, to, confirmation, timeout, maxRetries)
  }

  /** A response message indicating the given message couldn't be confirmed. */
  case class ConfirmationFailed(msg: PublicMessage)

  def props(messageGateway: ActorRef): Props = Props(new MessageForwarder(messageGateway))
}
