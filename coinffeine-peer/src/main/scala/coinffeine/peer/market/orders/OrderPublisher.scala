package coinffeine.peer.market.orders

import akka.actor.{Actor, ActorContext, ActorRef}

import coinffeine.model.market._
import coinffeine.model.order.OrderId
import coinffeine.peer.market.submission.SubmissionSupervisor._

private[orders] class OrderPublisher(
    orderId: OrderId,
    submissionActor: ActorRef,
    listener: OrderPublisher.Listener,
    policy: SubmissionPolicy = new SubmissionPolicyImpl)(implicit context: ActorContext) {

  private implicit val sender = context.self

  def setEntry(entry: OrderBookEntry): Unit = {
    policy.setEntry(entry)
    updateSubmissionRequest()
  }

  def unsetEntry(): Unit = {
    policy.unsetEntry()
    updateSubmissionRequest()
  }

  def updateSubmissionRequest(): Unit = {
    val message = policy.entryToSubmit.fold[Any](StopSubmitting(orderId))(KeepSubmitting.apply)
    submissionActor ! message
  }

  val receiveSubmissionEvents: Actor.Receive = {
    case InMarket(entryInMarket) if policy.currentEntry.contains(entryInMarket) =>
      listener.inMarket()
    case Offline(entryInMarket) if policy.currentEntry.contains(entryInMarket) =>
      listener.offline()
  }
}

private[orders] object OrderPublisher {
  trait Listener {
    def inMarket(): Unit
    def offline(): Unit
  }
}
