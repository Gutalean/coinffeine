package coinffeine.peer.market

import akka.actor._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.OrderBookEntry
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import coinffeine.protocol.messages.brokerage._

/** Submits and resubmits orders for a given market */
private class MarketSubmissionActor[C <: FiatCurrency](
     market: Market[C], gateway: ActorRef, protocolConstants: ProtocolConstants)
  extends Actor with ActorLogging {

  type SubmittingOrders = Set[(ActorRef, OrderBookEntry[C])]
  val SubmittingOrders = Set

  override def receive: Receive = waitingForOrders

  private def waitingForOrders: Receive = handleOpenOrders(SubmittingOrders.empty)

  private def keepingOpenOrders(orders: SubmittingOrders): Receive =
    handleOpenOrders(orders).orElse {

      case StopSubmitting(orderId) =>
        val newOrders = orders.filterNot { case (_, entry) => entry.id == orderId}
        forwardOrders(newOrders)
        context.become(
          if (newOrders.isEmpty) waitingForOrders
          else keepingOpenOrders(newOrders)
        )

      case ReceiveTimeout =>
        forwardOrders(orders)
    }

  private def handleOpenOrders(orders: SubmittingOrders): Receive = {

    case KeepSubmitting(order: OrderBookEntry[C]) if order.price.currency == market.currency =>
      val newOrders = orders + (sender() -> order)
      forwardOrders(newOrders)
      context.become(keepingOpenOrders(newOrders))
  }

  private def forwardOrders(orders: SubmittingOrders): Unit = {
    context.actorOf(
      PeerPositionsSubmitter.props(market, orders, gateway, protocolConstants),
      s"forward${orders.hashCode()}"
    )
    context.setReceiveTimeout(protocolConstants.orderResubmitInterval)
  }
}

private[market] object MarketSubmissionActor {
  def props(market: Market[_ <: FiatCurrency], gateway: ActorRef, constants: ProtocolConstants) =
    Props(new MarketSubmissionActor(market, gateway, constants))
}
