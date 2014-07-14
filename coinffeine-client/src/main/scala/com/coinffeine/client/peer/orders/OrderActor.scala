package com.coinffeine.client.peer.orders

import akka.actor.{Actor, ActorRef, Props}
import com.coinffeine.client.api.CoinffeineApp
import com.coinffeine.client.event.EventProducer

import com.coinffeine.client.peer.orders.OrderActor.{CancelOrder, Initialize, RetrieveStatus}
import com.coinffeine.client.peer.orders.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import com.coinffeine.common.{FiatAmount, OrderBookEntry}

class OrderActor extends Actor {

  override def receive: Receive = {
    case init: Initialize =>
      new InitializedOrderActor(init).start()
  }

  private class InitializedOrderActor(init: Initialize) extends EventProducer(init.eventChannel) {
    import init._

    def start(): Unit = {
      produceEvent(CoinffeineApp.OrderSubmittedEvent(order))
      init.submissionSupervisor ! KeepSubmitting(init.order)
      context.become(manageOrder)
    }

    private val manageOrder: Receive = {
      case CancelOrder =>
        submissionSupervisor ! StopSubmitting(order.id)
        produceEvent(CoinffeineApp.OrderCancelledEvent(order.id))

      case RetrieveStatus =>
        sender() ! order
    }
  }
}

object OrderActor {

  case class Initialize(order: OrderBookEntry[FiatAmount],
                        submissionSupervisor: ActorRef,
                        eventChannel: ActorRef,
                        messageGateway: ActorRef)

  case object CancelOrder

  /** Ask for order status. To be replied with an [[com.coinffeine.common.OrderBookEntry]].
    *
    * TODO: return an Order instead of an OrderBookEntry
    */
  case object RetrieveStatus

  trait Component {
    lazy val orderActorProps: Props = Props(new OrderActor)
  }
}
