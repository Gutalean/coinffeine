package coinffeine.peer.market

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.peer.api.event.{OrderSubmittedEvent, OrderUpdatedEvent}
import coinffeine.peer.event.EventProducer
import coinffeine.peer.market.OrderActor.{CancelOrder, Initialize, RetrieveStatus}
import coinffeine.peer.market.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderActor extends Actor {

  override def receive: Receive = {
    case init: Initialize =>
      new InitializedOrderActor(init).start()
  }

  private class InitializedOrderActor(init: Initialize) extends EventProducer(init.eventChannel) {
    import init._

    var currentOrder = init.order

    def start(): Unit = {
      messageGateway ! MessageGateway.Subscribe {
        case ReceiveMessage(orderMatch: OrderMatch, `brokerId`) => orderMatch.orderId == order.id
        case _ => false
      }

      // TODO: receive a confirmation that the order was accepted in the market
      // Since the order submission cannot be confirmed, the only thing we can do with the order
      // is to set its status to `InMarketOrder` before producing the `OrderSubmittedEvent`.
      // In the future, we should receive a confirmation that the order was accepted in the market
      // and then send a `OrderUpdatedEvent` with the new status
      currentOrder = order.withStatus(InMarketOrder)
      produceEvent(OrderSubmittedEvent(currentOrder))
      init.submissionSupervisor ! KeepSubmitting(OrderBookEntry(currentOrder))
      context.become(manageOrder)
    }

    private val manageOrder: Receive = {
      case CancelOrder =>
        // TODO: determine the cancellation reason
        currentOrder = currentOrder.withStatus(CancelledOrder("unknown reason"))
        submissionSupervisor ! StopSubmitting(currentOrder.id)
        produceEvent(OrderUpdatedEvent(currentOrder))

      case RetrieveStatus =>
        sender() ! currentOrder

      case orderMatch: OrderMatch =>
        init.submissionSupervisor ! StopSubmitting(orderMatch.orderId)
        // TODO: create the exchange, update currentOrder and send an OrderUpdatedEvent
        currentOrder = currentOrder.withStatus(CompletedOrder)
        produceEvent(OrderUpdatedEvent(currentOrder))
    }
  }
}

object OrderActor {

  case class Initialize(order: Order[FiatCurrency],
                        submissionSupervisor: ActorRef,
                        eventChannel: ActorRef,
                        messageGateway: ActorRef,
                        paymentProcessor: ActorRef,
                        wallet: ActorRef,
                        brokerId: PeerId)

  case object CancelOrder

  /** Ask for order status. To be replied with an [[Order]]. */
  case object RetrieveStatus

  trait Component {
    lazy val orderActorProps: Props = Props(new OrderActor)
  }
}
