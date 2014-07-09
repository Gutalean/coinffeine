package com.coinffeine.client.peer.orders

import akka.actor._

import com.coinffeine.client.api.CoinffeineApp
import com.coinffeine.client.event.EventProducer
import com.coinffeine.client.peer.CoinffeinePeerActor.{CancelOrder, OpenOrder, RetrieveOpenOrders}
import com.coinffeine.common.{FiatCurrency, PeerConnection}
import com.coinffeine.common.protocol.ProtocolConstants
import com.coinffeine.common.protocol.gateway.MessageGateway.ForwardMessage
import com.coinffeine.common.protocol.messages.brokerage._

/** Submits and resubmits orders for a given market */
private[orders] class OrderSubmissionActor(protocolConstants: ProtocolConstants)
  extends Actor with ActorLogging {

  override def receive: Receive = {
    case OrderSubmissionActor.Initialize(market, eventChannel, gateway, brokerAddress) =>
      new InitializedOrderSubmission(market, eventChannel, gateway, brokerAddress).start()
  }

  private class InitializedOrderSubmission(
      market: Market[FiatCurrency],
      eventChannel: ActorRef,
      gateway: ActorRef,
      broker: PeerConnection) extends EventProducer(eventChannel) {

    def start(): Unit = {
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = handleOpenOrders(OrderSet.empty(market))

    private def keepingOpenOrders(orderSet: OrderSet[FiatCurrency]): Receive =
      handleOpenOrders(orderSet).orElse {

        case CancelOrder(order) =>
          val reducedOrderSet = orderSet.cancelOrder(
            order.orderType, order.amount, order.price)
          forwardOrders(reducedOrderSet)

          produceEvent(CoinffeineApp.OrderCancelledEvent(order))

          context.become(
            if (reducedOrderSet.isEmpty) waitingForOrders
            else keepingOpenOrders(reducedOrderSet)
          )

        case ReceiveTimeout =>
          forwardOrders(orderSet)
      }

    private def handleOpenOrders(orderSet: OrderSet[FiatCurrency]): Receive = {
      case OpenOrder(order) =>
        val mergedOrderSet = orderSet.addOrder(
          order.orderType, order.amount, order.price)
        forwardOrders(mergedOrderSet)

        produceEvent(CoinffeineApp.OrderSubmittedEvent(order))

        context.become(keepingOpenOrders(mergedOrderSet))

      case RetrieveOpenOrders => sender() ! orderSet.orders.toSet
    }

    private def forwardOrders(orderSet: OrderSet[FiatCurrency]): Unit = {
      gateway ! ForwardMessage(orderSet, broker)
      context.setReceiveTimeout(protocolConstants.orderResubmitInterval)
    }
  }
}

private[orders] object OrderSubmissionActor {

  case class Initialize(market: Market[FiatCurrency],
                        eventChannel: ActorRef,
                        gateway: ActorRef,
                        brokerAddress: PeerConnection)

  def props(constants: ProtocolConstants) = Props(new OrderSubmissionActor(constants))
}
