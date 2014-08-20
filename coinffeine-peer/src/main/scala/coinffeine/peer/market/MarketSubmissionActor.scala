package coinffeine.peer.market

import akka.actor._

import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.market.OrderBookEntry
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import coinffeine.protocol.messages.brokerage._

/** Submits and resubmits orders for a given market */
private[market] class MarketSubmissionActor(protocolConstants: ProtocolConstants)
  extends Actor with ActorLogging {

  override def receive: Receive = {
    case init @ MarketSubmissionActor.Initialize(_, _) =>
      new InitializedOrderSubmission(init).start()
  }

  private class InitializedOrderSubmission[C <: FiatCurrency](
      init: MarketSubmissionActor.Initialize[C]) {

    import init._
    implicit val executor = context.dispatcher

    type SubmittingOrders = Set[(ActorRef, OrderBookEntry[FiatAmount])]
    val SubmittingOrders = Set

    def start(): Unit = {
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = handleOpenOrders(SubmittingOrders.empty)

    private def keepingOpenOrders(orders: SubmittingOrders): Receive =
      handleOpenOrders(orders).orElse {

        case StopSubmitting(orderId) =>
          val newOrders = orders.filterNot{ case (_, entry) => entry.id == orderId }
          forwardOrders(newOrders)
          context.become(
            if (newOrders.isEmpty) waitingForOrders
            else keepingOpenOrders(newOrders)
          )

        case ReceiveTimeout =>
          forwardOrders(orders)
      }

    private def handleOpenOrders(orders: SubmittingOrders): Receive = {

      case KeepSubmitting(order) =>
        val newOrders = orders + (sender -> order)
        forwardOrders(newOrders)
        context.become(keepingOpenOrders(newOrders))
    }

    private def forwardOrders(orders: SubmittingOrders): Unit = {
      context.actorOf(PeerPositionsSubmitter.props(
        market, orders, registry, protocolConstants.orderAcknowledgeTimeout))
      context.setReceiveTimeout(protocolConstants.orderResubmitInterval)
    }
  }
}

private[market] object MarketSubmissionActor {

  case class Initialize[C <: FiatCurrency](market: Market[C], registry: ActorRef)

  def props(constants: ProtocolConstants) = Props(new MarketSubmissionActor(constants))
}
