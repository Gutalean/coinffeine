package com.coinffeine.common.protocol.gateway

import akka.actor._

import com.coinffeine.common.protocol.gateway.MessageGateway._
import com.coinffeine.common.protocol.messages.PublicMessage

private class SubscriptionManagerActor extends Actor with ActorLogging {

  /** Metadata on message subscription requested by an actor. */
  private case class MessageSubscription(filter: Filter)

  private var subscriptions = Map.empty[ActorRef, MessageSubscription]

  override def receive: Receive = {
    case Subscribe(filter) =>
      subscriptions += sender -> MessageSubscription(filter)
    case Unsubscribe =>
      subscriptions -= sender
    case message @ ReceiveMessage(_, _) =>
      dispatchToSubscriptions(message)
  }

  private def dispatchToSubscriptions(notification: ReceiveMessage[_ <: PublicMessage]): Unit = {
    for ((actor, MessageSubscription(filter)) <- subscriptions if filter(notification)) {
      actor ! notification
    }
  }
}

private[gateway] object SubscriptionManagerActor {
  val props: Props = Props(new SubscriptionManagerActor())
}
