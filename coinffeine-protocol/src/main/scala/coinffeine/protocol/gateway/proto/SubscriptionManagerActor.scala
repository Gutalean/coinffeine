package coinffeine.protocol.gateway.proto

import akka.actor._

import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.PublicMessage

private class SubscriptionManagerActor extends Actor with ActorLogging {
  import SubscriptionManagerActor._

  private var subscriptions = Map.empty[ActorRef, ReceiveFilter]
  private var brokerSubscriptions = Map.empty[ActorRef, MessageFilter]

  override def receive: Receive = {
    case Subscribe(filter) =>
      addSubscription(sender(), filter)
    case SubscribeToBroker(filter) =>
      addBrokerSubscription(sender(), filter)
    case Unsubscribe =>
      subscriptions -= sender
      brokerSubscriptions -= sender
    case notification: NotifySubscribers =>
      dispatchToSubscriptions(notification)
  }

  private def addSubscription(subscriber: ActorRef, filter: ReceiveFilter): Unit = {
    val newFilter = subscriptions.get(subscriber).fold(filter)(_ orElse filter)
    subscriptions += subscriber -> newFilter
  }

  private def addBrokerSubscription(subscriber: ActorRef, filter: MessageFilter): Unit = {
    val newFilter = brokerSubscriptions.get(subscriber).fold(filter)(_ orElse filter)
    brokerSubscriptions += subscriber -> newFilter
  }

  private def dispatchToSubscriptions(notification: NotifySubscribers): Unit = {
    for (actor <- subscriptions.keySet ++ brokerSubscriptions.keySet
         if isSubscribed(actor, notification)) {
      actor ! notification.message
    }
  }

  private def isSubscribed(subscriber: ActorRef, notification: NotifySubscribers): Boolean =
    isSubscribedToPeerMessage(subscriber, notification) ||
      isSubscribedToBrokerMessage(subscriber, notification)

  private def isSubscribedToPeerMessage(subscriber: ActorRef, notification: NotifySubscribers) =
    subscriptions.get(subscriber).fold(false)(_.isDefinedAt(notification.message))

  private def isSubscribedToBrokerMessage(subscriber: ActorRef, notification: NotifySubscribers) =
    notification.fromBroker &&
      brokerSubscriptions.get(subscriber).fold(false)(_.isDefinedAt(notification.message.msg))
}

private[gateway] object SubscriptionManagerActor {
  val props: Props = Props(new SubscriptionManagerActor())

  case class NotifySubscribers(message: ReceiveMessage[_ <: PublicMessage], fromBroker: Boolean)
}
