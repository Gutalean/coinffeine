package com.coinffeine.client.peer.orders

import akka.actor._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway.ForwardMessage
import coinffeine.protocol.messages.brokerage._
import com.coinffeine.client.peer.orders.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import com.coinffeine.common.ProtocolConstants

/** Submits and resubmits orders for a given market */
private[orders] class MarketSubmissionActor(protocolConstants: ProtocolConstants)
  extends Actor with ActorLogging {

  override def receive: Receive = {
    case init: MarketSubmissionActor.Initialize[FiatCurrency] =>
      new InitializedOrderSubmission(init).start()
  }

  private class InitializedOrderSubmission[C <: FiatCurrency](
      init: MarketSubmissionActor.Initialize[C]) {

    import init._

    def start(): Unit = {
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = handleOpenOrders(PeerOrderRequests.empty(market))

    private def keepingOpenOrders(requests: PeerOrderRequests[FiatCurrency]): Receive =
      handleOpenOrders(requests).orElse {

        case StopSubmitting(orderId) =>
          val reducedOrderSet =
            PeerOrderRequests(market, requests.entries.filterNot(_.id == orderId))
          forwardOrders(reducedOrderSet)

          context.become(
            if (reducedOrderSet.entries.isEmpty) waitingForOrders
            else keepingOpenOrders(reducedOrderSet)
          )

        case ReceiveTimeout =>
          forwardOrders(requests)
      }

    private def handleOpenOrders(positions: PeerOrderRequests[FiatCurrency]): Receive = {

      case KeepSubmitting(order) =>
        val mergedOrderSet = positions.addEntry(order)
        forwardOrders(mergedOrderSet)
        context.become(keepingOpenOrders(mergedOrderSet))
    }

    private def forwardOrders(orderSet: PeerOrderRequests[FiatCurrency]): Unit = {
      gateway ! ForwardMessage(orderSet, brokerId)
      context.setReceiveTimeout(protocolConstants.orderResubmitInterval)
    }
  }
}

private[orders] object MarketSubmissionActor {

  case class Initialize[C <: FiatCurrency](market: Market[C],
                                           gateway: ActorRef,
                                           brokerId: PeerId)

  def props(constants: ProtocolConstants) = Props(new MarketSubmissionActor(constants))
}
