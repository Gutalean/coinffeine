package com.coinffeine.client.peer.orders

import akka.actor._

import com.coinffeine.client.api.CoinffeineApp
import com.coinffeine.client.event.EventProducer
import com.coinffeine.client.peer.orders.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import com.coinffeine.common.FiatCurrency
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.protocol.ProtocolConstants
import com.coinffeine.common.protocol.gateway.MessageGateway.ForwardMessage
import com.coinffeine.common.protocol.messages.brokerage._

/** Submits and resubmits orders for a given market */
private[orders] class MarketSubmissionActor(protocolConstants: ProtocolConstants)
  extends Actor with ActorLogging {

  override def receive: Receive = {
    case init: MarketSubmissionActor.Initialize[FiatCurrency] =>
      new InitializedOrderSubmission(init).start()
  }

  private class InitializedOrderSubmission[C <: FiatCurrency](init: MarketSubmissionActor.Initialize[C])
    extends EventProducer(init.eventChannel) {

    import init._

    def start(): Unit = {
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = handleOpenOrders(PeerPositions.empty(market))

    private def keepingOpenOrders(positions: PeerPositions[FiatCurrency]): Receive =
      handleOpenOrders(positions).orElse {

        case StopSubmitting(orderId) =>
          val reducedOrderSet =
            PeerPositions(market, positions.positions.filterNot(_.id == orderId))
          forwardOrders(reducedOrderSet)

          produceEvent(CoinffeineApp.OrderCancelledEvent(orderId))

          context.become(
            if (reducedOrderSet.positions.isEmpty) waitingForOrders
            else keepingOpenOrders(reducedOrderSet)
          )

        case ReceiveTimeout =>
          forwardOrders(positions)
      }

    private def handleOpenOrders(positions: PeerPositions[FiatCurrency]): Receive = {

      case KeepSubmitting(order) =>
        val mergedOrderSet = positions.addOrder(order)
        forwardOrders(mergedOrderSet)

        produceEvent(CoinffeineApp.OrderSubmittedEvent(order))

        context.become(keepingOpenOrders(mergedOrderSet))
    }

    private def forwardOrders(orderSet: PeerPositions[FiatCurrency]): Unit = {
      gateway ! ForwardMessage(orderSet, brokerId)
      context.setReceiveTimeout(protocolConstants.orderResubmitInterval)
    }
  }
}

private[orders] object MarketSubmissionActor {

  case class Initialize[C <: FiatCurrency](market: Market[C],
                                           eventChannel: ActorRef,
                                           gateway: ActorRef,
                                           brokerId: PeerId)

  def props(constants: ProtocolConstants) = Props(new MarketSubmissionActor(constants))
}
