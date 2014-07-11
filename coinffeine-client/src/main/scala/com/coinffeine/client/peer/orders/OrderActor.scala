package com.coinffeine.client.peer.orders

import akka.actor.{Actor, ActorRef, Props}

import com.coinffeine.client.peer.orders.OrderActor.{CancelOrder, Initialize, RetrieveStatus}
import com.coinffeine.client.peer.orders.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import com.coinffeine.common.{FiatAmount, Order}

class OrderActor extends Actor {

  var submissionSupervisor: ActorRef = _
  var order: Order[FiatAmount] = _

  override def receive: Receive = {
    case init: Initialize =>
      submissionSupervisor = init.submissionSupervisor
      order = init.order
      submissionSupervisor ! KeepSubmitting(order)

    case CancelOrder =>
      submissionSupervisor ! StopSubmitting(order.id)

    case RetrieveStatus =>
      sender() ! order
  }
}

object OrderActor {

  case class Initialize(order: Order[FiatAmount], submissionSupervisor: ActorRef)
  case object CancelOrder

  /** Ask for order status. To be replied with an [[com.coinffeine.common.Order]]. */
  case object RetrieveStatus

  trait Component {
    lazy val orderActorProps: Props = Props(new OrderActor)
  }
}
