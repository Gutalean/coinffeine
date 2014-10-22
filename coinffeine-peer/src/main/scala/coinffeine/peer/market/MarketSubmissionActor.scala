package coinffeine.peer.market

import akka.actor._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Market, OrderBookEntry}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import coinffeine.protocol.messages.brokerage._

/** Submits and resubmits orders for a given market */
private class MarketSubmissionActor[C <: FiatCurrency](
     market: Market[C], gateway: ActorRef, protocolConstants: ProtocolConstants)
  extends Actor with ActorLogging with Stash {

  type SubmittingOrders = Set[(ActorRef, OrderBookEntry[C])]
  val SubmittingOrders = Set

  private var currentForwarder: Option[ActorRef] = None

  override def receive: Receive = waitingForOrders

  private def waitingForOrders: Receive = handleOpenOrders(SubmittingOrders.empty)

  private def keepingOpenOrders(orders: SubmittingOrders): Receive =
    handleOpenOrders(orders).orElse {

      case StopSubmitting(orderId) =>
        val newOrders = orders.filterNot { case (_, entry) => entry.id == orderId}
        forwardOrders(newOrders,
          if (newOrders.isEmpty) waitingForOrders
          else keepingOpenOrders(newOrders))

      case ReceiveTimeout =>
        forwardOrders(orders, keepingOpenOrders(orders))

      case Terminated(child) if currentForwarder.contains(child) =>
        currentForwarder = None
    }

  private def handleOpenOrders(orders: SubmittingOrders): Receive = {

    case KeepSubmitting(order: OrderBookEntry[C]) if order.price.currency == market.currency =>
      val newOrders = orders + (sender() -> order)
      forwardOrders(newOrders, keepingOpenOrders(newOrders))

    case Terminated(child) if currentForwarder.contains(child) =>
      currentForwarder = None
  }

  private def forwardOrders(orders: SubmittingOrders,
                            continuation: Receive): Unit = currentForwarder match {
    case Some(forwarder) =>
      context.stop(forwarder)
      context.become(replacingForwarder(forwarder, orders, continuation))
    case None =>
      spawnForwarder(orders)
      context.become(continuation)
  }

  private def replacingForwarder(forwarder: ActorRef,
                                 newOrders: SubmittingOrders,
                                 continuation: Receive): Receive = {
    case Terminated(`forwarder`) =>
      spawnForwarder(newOrders)
      unstashAll()
      context.become(continuation)
    case msg =>
      stash()
  }

  private def spawnForwarder(orders: SubmittingOrders): Unit = {
    val forwarder = context.actorOf(
      PeerPositionsSubmitter.props(market, orders, gateway, protocolConstants),
      s"forward${orders.hashCode()}"
    )
    context.watch(forwarder)
    currentForwarder = Some(forwarder)
    context.setReceiveTimeout(protocolConstants.orderResubmitInterval)
  }
}

private[market] object MarketSubmissionActor {
  def props(market: Market[_ <: FiatCurrency], gateway: ActorRef, constants: ProtocolConstants) =
    Props(new MarketSubmissionActor(market, gateway, constants))
}
