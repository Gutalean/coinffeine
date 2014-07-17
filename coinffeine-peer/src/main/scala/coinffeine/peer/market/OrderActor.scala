package coinffeine.peer.market

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.model.currency.FiatAmount
import coinffeine.model.market.OrderBookEntry
import coinffeine.peer.api.event.{OrderCancelledEvent, OrderSubmittedEvent}
import coinffeine.peer.event.EventProducer
import coinffeine.peer.market.OrderActor.{CancelOrder, Initialize, RetrieveStatus}
import coinffeine.peer.market.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderActor extends Actor {

  override def receive: Receive = {
    case init: Initialize =>
      new InitializedOrderActor(init).start()
  }

  private class InitializedOrderActor(init: Initialize) extends EventProducer(init.eventChannel) {
    import init._

    def start(): Unit = {
      messageGateway ! MessageGateway.Subscribe {
        case o: OrderMatch if o.orderId == order.id => true
        case _ => false
      }
      produceEvent(OrderSubmittedEvent(order))
      init.submissionSupervisor ! KeepSubmitting(init.order)
      context.become(manageOrder)
    }

    private val manageOrder: Receive = {
      case CancelOrder =>
        submissionSupervisor ! StopSubmitting(order.id)
        produceEvent(OrderCancelledEvent(order.id))

      case RetrieveStatus =>
        sender() ! order

      case orderMatch: OrderMatch =>
        init.submissionSupervisor ! StopSubmitting(orderMatch.orderId)
        /* TODO: send a more appropriate event since this is not a
         * cancellation but an exchange-started event
         */
        produceEvent(OrderCancelledEvent(orderMatch.orderId))
    }
  }
}

object OrderActor {

  case class Initialize(order: OrderBookEntry[FiatAmount],
                        submissionSupervisor: ActorRef,
                        eventChannel: ActorRef,
                        messageGateway: ActorRef,
                        paymentProcessor: ActorRef,
                        wallet: ActorRef)

  case object CancelOrder

  /** Ask for order status. To be replied with an [[OrderBookEntry]].
    *
    * TODO: return an Order instead of an OrderBookEntry
    */
  case object RetrieveStatus

  trait Component {
    lazy val orderActorProps: Props = Props(new OrderActor)
  }
}
