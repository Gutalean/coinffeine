package coinffeine.peer.market.submission

import scala.concurrent.duration.FiniteDuration

import akka.actor._

import coinffeine.common.akka.ResubmitTimer
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{Market, OrderBookEntry}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.market.submission.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}

/** Submits and resubmits orders for a given market */
private class MarketSubmissionActor[C <: FiatCurrency](
     market: Market[C], 
     forwarderProps: SubmittingOrders[C] => Props,
     resubmitInterval: FiniteDuration) extends Actor with ActorLogging with Stash {

  private val timer = new ResubmitTimer(context, resubmitInterval)
  private var currentForwarder: Option[ActorRef] = None

  override def preStart(): Unit = { timer.start() }
  override def postStop(): Unit = { timer.cancel() }

  override def receive: Receive = handleOpenOrders(SubmittingOrders.empty)

  private def handleOpenOrders(orders: SubmittingOrders[C]): Receive = {

    case KeepSubmitting(order: OrderBookEntry[C]) if order.price.currency == market.currency =>
      val newOrders = orders + (sender() -> order)
      if (newOrders != orders) {
        timer.reset()
        forwardOrders(newOrders)
      }

    case StopSubmitting(orderId) =>
      val newOrders = orders.filterNot { case (_, entry) => entry.id == orderId }
      forwardOrders(newOrders)

    case ResubmitTimer.ResubmitTimeout if orders.nonEmpty => forwardOrders(orders)

    case Terminated(child) if currentForwarder.contains(child) => currentForwarder = None
  }

  private def forwardOrders(orders: SubmittingOrders[C]): Unit = {

    def replaceForwarder(forwarder: ActorRef): Unit = {
      context.stop(forwarder)
      context.become {
        case Terminated(`forwarder`) =>
          unstashAll()
          startForwarder()
        case _ => stash()
      }
    }

    def startForwarder(): Unit = {
      spawnForwarder(orders)
      context.become(handleOpenOrders(orders))
    }

    currentForwarder.fold(startForwarder())(replaceForwarder)
  }

  private def spawnForwarder(orders: SubmittingOrders[C]): Unit = {
    val forwarder = context.actorOf(forwarderProps(orders), s"forward${orders.hashCode()}")
    context.watch(forwarder)
    currentForwarder = Some(forwarder)
  }
}

private[market] object MarketSubmissionActor {

  def props[C <: FiatCurrency](market: Market[C], gateway: ActorRef, constants: ProtocolConstants): Props =
    Props(new MarketSubmissionActor[C](
      market,
      orders => PeerPositionsSubmitter.props(market, orders, gateway, constants),
      constants.orderResubmitInterval
    ))
  
  def props[C <: FiatCurrency](market: Market[C],
                               forwarderProps: SubmittingOrders[C] => Props,
                               resubmitInterval: FiniteDuration): Props =
    Props(new MarketSubmissionActor(market, forwarderProps, resubmitInterval))
}
