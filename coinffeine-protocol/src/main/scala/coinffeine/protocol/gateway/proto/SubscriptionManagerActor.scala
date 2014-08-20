package coinffeine.protocol.gateway.proto

import akka.actor._

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.proto.SubscriptionManagerActor.ConnectedToBroker
import coinffeine.protocol.messages.PublicMessage

private class SubscriptionManagerActor extends Actor with ActorLogging {

  private var subscriptions = Map.empty[ActorRef, ReceiveFilter]
  private var brokerSubscriptions = Map.empty[ActorRef, MessageFilter]
  private var brokerId: Option[PeerId] = None

  override def receive: Receive = {
    case Subscribe(filter) =>
      addSubscription(sender(), filter)
    case SubscribeToBroker(filter) =>
      addBrokerSubscription(sender(), filter)
    case Unsubscribe =>
      subscriptions -= sender
      brokerSubscriptions -= sender
    case message @ ReceiveMessage(_, _) =>
      dispatchToSubscriptions(message)
    case ConnectedToBroker(id) =>
      brokerId = Some(id)
  }

  private def addSubscription(subscriber: ActorRef, filter: ReceiveFilter): Unit = {
    val newFilter = subscriptions.get(subscriber).fold(filter)(_ orElse filter)
    subscriptions += subscriber -> newFilter
  }

  private def addBrokerSubscription(subscriber: ActorRef, filter: MessageFilter): Unit = {
    val newFilter = brokerSubscriptions.get(subscriber).fold(filter)(_ orElse filter)
    brokerSubscriptions += subscriber -> newFilter
  }

  private def dispatchToSubscriptions(notification: ReceiveMessage[_ <: PublicMessage]): Unit = {
    for (actor <- subscriptions.keySet ++ brokerSubscriptions.keySet
         if isSubscribed(actor, notification)) {
      actor ! notification
    }
  }

  private def isSubscribed(subscriber: ActorRef,
                           notification: ReceiveMessage[_ <: PublicMessage]): Boolean =
    subscriptions.get(subscriber).fold(false)(_.isDefinedAt(notification)) ||
      (isFromBroker(notification) &&
        brokerSubscriptions.get(subscriber).fold(false)(_.isDefinedAt(notification.msg)))

  private def isFromBroker(notification: ReceiveMessage[_ <: PublicMessage]): Boolean = {
    Some(notification.sender) == brokerId
  }
}

private[gateway] object SubscriptionManagerActor {
  val props: Props = Props(new SubscriptionManagerActor())

  case class ConnectedToBroker(brokerId: PeerId)
}
