package coinffeine.protocol.gateway

import scala.concurrent.duration._

import akka.actor._
import akka.util.Timeout

import coinffeine.protocol.gateway.MessageForwarder.RetrySettings
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage, Subscribe}
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
class MessageForwarder[A](requester: ActorRef,
                          messageGateway: ActorRef,
                          message: ForwardMessage[_ <: PublicMessage],
                          confirmation: PartialFunction[PublicMessage, A],
                          retry: RetrySettings = MessageForwarder.DefaultRetrySettings)
    extends Actor with ActorLogging {

  override val receive = waitForConfirmation(retry.maxRetries)

  override def preStart() = {
    subscribeToDestinationMessages()
    forwardMessage()
    setReceptionTimeout()
  }

  private def subscribeToDestinationMessages(): Unit = {
    messageGateway ! Subscribe {
      case ReceiveMessage(msg, message.`dest`) if confirmation.isDefinedAt(msg) =>
    }
  }

  private def forwardMessage(): Unit = {
    messageGateway ! message
  }

  private def setReceptionTimeout(): Unit = {
    context.setReceiveTimeout(retry.timeout.duration)
  }

  private def waitForConfirmation(remainingRetries: Int): Receive = {
    case ReceiveMessage(response, message.`dest`) if confirmation.isDefinedAt(response) =>
      requester ! confirmation(response)
      terminate()
    case ReceiveTimeout if remainingRetries > 0 =>
      forwardMessage()
      context.become(waitForConfirmation(remainingRetries - 1))
    case ReceiveTimeout =>
      requester ! MessageForwarder.ConfirmationFailed(message.message)
      terminate()
  }

  private def terminate(): Unit = {
    self ! PoisonPill
  }
}

object MessageForwarder {

  val DefaultTimeout = Timeout(5.seconds)
  val DefaultMaxRetries = 3

  case class RetrySettings(timeout: Timeout = DefaultTimeout, maxRetries: Int = DefaultMaxRetries) {
    require(maxRetries >= 0, "number of retries must not be negative")
  }
  object RetrySettings {
    val Continuously = RetrySettings(DefaultTimeout, Int.MaxValue)
    def continuouslyEvery(interval: FiniteDuration) = RetrySettings(Timeout(interval), Int.MaxValue)
  }

  val DefaultRetrySettings = RetrySettings()

  /** A response message indicating the given message couldn't be confirmed. */
  case class ConfirmationFailed(msg: PublicMessage)

  def props[A](requester: ActorRef,
               messageGateway: ActorRef,
               msg: ForwardMessage[_ <: PublicMessage],
               confirmation: PartialFunction[PublicMessage, A],
               retry: RetrySettings = MessageForwarder.DefaultRetrySettings): Props =
    Props(new MessageForwarder(requester, messageGateway, msg, confirmation, retry))

  /** A factory of forward messages that operates with a fixed message gateway and actor context.
    *
    * It is specially useful instantiated once in an actor and used many times to forward
    * indicating only the message, the destination and the confirmation.
    */
  class Factory(messageGateway: ActorRef, context: ActorContext, retry: RetrySettings) {

    def forward[A](msg: ForwardMessage[_ <: PublicMessage])
                  (confirmation: PartialFunction[PublicMessage, A]) : ActorRef =
      context.actorOf(props(context.self, messageGateway, msg, confirmation, retry))
  }

  object Factory {
    def apply(messageGateway: ActorRef,
              retry: RetrySettings = MessageForwarder.DefaultRetrySettings)
             (implicit context: ActorContext): Factory =
      new Factory(messageGateway, context, retry)
  }
}
