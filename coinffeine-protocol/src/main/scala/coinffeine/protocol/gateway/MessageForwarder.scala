package coinffeine.protocol.gateway

import scala.concurrent.duration._

import akka.actor._
import akka.util.Timeout

import coinffeine.model.network.NodeId
import coinffeine.protocol.gateway.MessageForwarder.RetrySettings
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
                          message: PublicMessage,
                          destination: NodeId,
                          confirmation: PartialFunction[PublicMessage, A],
                          retry: RetrySettings = MessageForwarder.DefaultRetrySettings)
    extends Actor with ActorLogging {

  import MessageForwarder._
  import MessageGateway._

  override val receive = waitForConfirmation(retry.maxRetries)

  override def preStart() = {
    subscribeToDestinationMessages()
    forwardMessageToDestination()
    setReceptionTimeout()
  }

  private def subscribeToDestinationMessages(): Unit = {
    messageGateway ! Subscribe {
      case ReceiveMessage(msg, `destination`) if confirmation.isDefinedAt(msg) =>
    }
  }

  private def forwardMessageToDestination(): Unit = {
    messageGateway ! ForwardMessage(message, destination)
  }

  private def setReceptionTimeout(): Unit = {
    context.setReceiveTimeout(retry.timeout.duration)
  }

  private def terminate(): Unit = {
    context.stop(self)
  }

  private def waitForConfirmation(remainingRetries: Int): Receive = {
    case ReceiveMessage(response, `destination`) if confirmation.isDefinedAt(response) =>
      requester ! confirmation(response)
      terminate()
    case ReceiveTimeout if remainingRetries > 0 =>
      forwardMessageToDestination()
      context.become(waitForConfirmation(remainingRetries - 1))
    case ReceiveTimeout =>
      requester ! ConfirmationFailed(message)
      terminate()
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
               msg: PublicMessage,
               destination: NodeId,
               confirmation: PartialFunction[PublicMessage, A],
               retry: RetrySettings = MessageForwarder.DefaultRetrySettings): Props =
    Props(new MessageForwarder(requester, messageGateway, msg, destination, confirmation, retry))

  /** A factory of forward messages that operates with a fixed message gateway and actor context.
    *
    * It is specially useful instantiated once in an actor and used many times to forward
    * indicating only the message, the destination and the confirmation.
    */
  class Factory(messageGateway: ActorRef, context: ActorContext) {

    def forward[A](msg: PublicMessage,
                   destination: NodeId,
                   retry: RetrySettings = MessageForwarder.DefaultRetrySettings)
                  (confirmation: PartialFunction[PublicMessage, A]) : ActorRef =
      context.actorOf(props(context.self, messageGateway, msg, destination, confirmation, retry))
  }

  object Factory {

    def apply(messageGateway: ActorRef)(implicit context: ActorContext): Factory =
      new Factory(messageGateway, context)
  }
}
