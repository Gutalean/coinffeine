package com.coinffeine.client.peer.orders

import akka.actor.{ActorRef, Props}
import com.coinffeine.common.{FiatAmount, Order}

object OrderActor {

  case class Initialize(order: Order[FiatAmount], submissionSupervisor: ActorRef)
  case object CancelOrder

  /** Ask for order status. To be replied with an [[com.coinffeine.common.Order]]. */
  case object RetrieveStatus

  trait Component {

    lazy val orderActorProps: Props = null
  }
}
