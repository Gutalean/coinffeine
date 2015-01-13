package coinffeine.protocol.gateway

import akka.actor._

import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.PublicMessage

private class SubscriptionManagerActor extends Actor with ActorLogging {
  import SubscriptionManagerActor._

  private var subscriptions = Map.empty[ActorRef, ReceiveFilter]

  override def receive: Receive = {
    case Subscribe(filter) =>
      addSubscription(sender(), filter)
    case Unsubscribe =>
      subscriptions -= sender
    case notification: NotifySubscribers =>
      dispatchToSubscriptions(notification)
  }

  private def addSubscription(subscriber: ActorRef, filter: ReceiveFilter): Unit = {
    val newFilter = subscriptions.get(subscriber).fold(filter)(_ orElse filter)
    subscriptions += subscriber -> newFilter
  }

  private def dispatchToSubscriptions(notification: NotifySubscribers): Unit = {
    for (actor <- subscriptions.keySet if isSubscribed(actor, notification)) {
      actor ! notification.message
    }
  }

  private def isSubscribed(subscriber: ActorRef, notification: NotifySubscribers): Boolean =
    subscriptions.get(subscriber).fold(false)(_.isDefinedAt(notification.message))
}

private[gateway] object SubscriptionManagerActor {
  val props: Props = Props(new SubscriptionManagerActor())

  case class NotifySubscribers(message: ReceiveMessage[_ <: PublicMessage])
}
