package coinffeine.peer.market.orders

import akka.actor.{Actor, ActorContext, ActorRef}

import coinffeine.model.market._
import coinffeine.peer.market.submission.SubmissionSupervisor._

private[orders] class OrderPublisher(submissionActor: ActorRef,
                                                        listener: OrderPublisher.Listener)
                                                       (implicit context: ActorContext) {

  private implicit val sender = context.self
  private var pendingEntry: Option[OrderBookEntry] = None

  def keepPublishing(entry: OrderBookEntry): Unit = {
    pendingEntry = Some(entry)
    submissionActor ! KeepSubmitting(entry)
  }

  def stopPublishing(): Unit = {
    pendingEntry.foreach { entry =>
      submissionActor ! StopSubmitting(entry.id)
    }
  }

  val receiveSubmissionEvents: Actor.Receive = {
    case InMarket(entryInMarket) if Some(entryInMarket) == pendingEntry => listener.inMarket()
    case Offline(entryInMarket) if Some(entryInMarket) == pendingEntry => listener.offline()
  }
}

private[orders] object OrderPublisher {
  trait Listener {
    def inMarket(): Unit
    def offline(): Unit
  }
}
