package coinffeine.peer.market.orders

import akka.actor.{Actor, ActorContext, ActorRef}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market._
import coinffeine.peer.market.submission.SubmissionSupervisor._

private[orders] class OrderPublisher[C <: FiatCurrency](submissionActor: ActorRef,
                                                        listener: OrderPublisher.Listener)
                                                       (implicit context: ActorContext) {

  private implicit val sender = context.self
  private var pendingEntry: OrderBookEntry[C] = _

  def keepPublishing(entry: OrderBookEntry[C]): Unit = {
    pendingEntry = entry
    submissionActor ! KeepSubmitting(pendingEntry)
  }

  def stopPublishing(): Unit = {
    submissionActor ! StopSubmitting(pendingEntry.id)
  }

  val receiveSubmissionEvents: Actor.Receive = {
    case InMarket(entryInMarket) if entryInMarket == pendingEntry => listener.inMarket()
    case Offline(entryInMarket) if entryInMarket == pendingEntry => listener.offline()
  }
}

private[orders] object OrderPublisher {
  trait Listener {
    def inMarket(): Unit
    def offline(): Unit
  }
}
