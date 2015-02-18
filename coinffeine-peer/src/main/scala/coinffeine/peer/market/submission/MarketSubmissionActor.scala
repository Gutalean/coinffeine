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
     forwarderProps: Submission[C] => Props,
     resubmitInterval: FiniteDuration) extends Actor with ActorLogging with Stash {

  private val timer = new ResubmitTimer(context, resubmitInterval)
  private var currentForwarder: Option[ActorRef] = None

  override def preStart(): Unit = { timer.start() }
  override def postStop(): Unit = { timer.cancel() }

  override def receive: Receive = handleOpenOrders(Submission.empty(market))

  private def handleOpenOrders(submission: Submission[C]): Receive = {

    case KeepSubmitting(orderBookEntry: OrderBookEntry[C])
        if orderBookEntry.price.currency == market.currency =>
      val newSubmission = submission.addEntry(sender(), orderBookEntry)
      if (newSubmission != submission) {
        timer.reset()
        forwardOrders(newSubmission)
      }

    case StopSubmitting(orderId) => forwardOrders(submission.removeEntry(orderId))

    case ResubmitTimer.ResubmitTimeout if submission.entries.nonEmpty => forwardOrders(submission)

    case Terminated(child) if currentForwarder.contains(child) => currentForwarder = None
  }

  private def forwardOrders(submission: Submission[C]): Unit = {

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
      spawnForwarder(submission)
      context.become(handleOpenOrders(submission))
    }

    currentForwarder.fold(startForwarder())(replaceForwarder)
  }

  private def spawnForwarder(submission: Submission[C]): Unit = {
    val forwarder = context.actorOf(forwarderProps(submission), s"forward${submission.hashCode()}")
    context.watch(forwarder)
    currentForwarder = Some(forwarder)
  }
}

private[market] object MarketSubmissionActor {

  def props[C <: FiatCurrency](market: Market[C], gateway: ActorRef, constants: ProtocolConstants): Props =
    Props(new MarketSubmissionActor[C](
      market,
      submission => PeerPositionsSubmitter.props(submission, gateway, constants),
      constants.orderResubmitInterval
    ))

  def props[C <: FiatCurrency](market: Market[C],
                               forwarderProps: Submission[C] => Props,
                               resubmitInterval: FiniteDuration): Props =
    Props(new MarketSubmissionActor(market, forwarderProps, resubmitInterval))
}
