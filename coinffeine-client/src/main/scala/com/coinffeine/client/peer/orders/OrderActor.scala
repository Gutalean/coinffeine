package com.coinffeine.client.peer.orders

import akka.actor.{Actor, ActorRef, Props}

import com.coinffeine.client.peer.orders.OrderActor.{CancelOrder, Initialize, RetrieveStatus}
import com.coinffeine.client.peer.orders.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import com.coinffeine.common.{FiatAmount, Order}

class OrderActor extends Actor {

  override def receive: Receive = {
    case init: Initialize =>
      init.submissionSupervisor ! KeepSubmitting(init.order)
      context.become(manageOrder(init.order, init.submissionSupervisor))
  }

  def manageOrder(order: Order[FiatAmount], supervisor: ActorRef): Receive = {
    case CancelOrder =>
      supervisor ! StopSubmitting(order.id)

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
