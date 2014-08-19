package coinffeine.protocol.gateway.proto

import akka.actor._

import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.PublicMessage

private class SubscriptionManagerActor extends Actor with ActorLogging {

  private var subscriptions = Map.empty[ActorRef, ReceiveFilter]
  private var brokerSubscriptions = Map.empty[ActorRef, ]

  override def receive: Receive = {
    case Subscribe(filter) =>
      addSubscription(sender(), filter)
    case Unsubscribe =>
      subscriptions -= sender
    case message @ ReceiveMessage(_, _) =>
      dispatchToSubscriptions(message)
  }

  private def addSubscription(subscriber: ActorRef, filter: ReceiveFilter): Unit = {
    val newFilter = subscriptions.get(subscriber).fold(filter)(_ orElse filter)
    subscriptions += subscriber -> newFilter
  }

  private def dispatchToSubscriptions(notification: ReceiveMessage[_ <: PublicMessage]): Unit = {
    for ((actor, filter) <- subscriptions if filter.isDefinedAt(notification)) {
      actor ! notification
    }
  }
}

private[gateway] object SubscriptionManagerActor {
  val props: Props = Props(new SubscriptionManagerActor())
}
