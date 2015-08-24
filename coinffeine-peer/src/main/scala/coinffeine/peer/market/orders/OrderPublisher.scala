package coinffeine.peer.market.orders

import akka.actor.{Actor, ActorContext, ActorRef}

import coinffeine.model.market._
import coinffeine.model.order.OrderId
import coinffeine.peer.events.bitcoin.BitcoinBalanceChanged
import coinffeine.peer.events.fiat.FiatBalanceChanged
import coinffeine.peer.market.submission.SubmissionSupervisor._

private[orders] class OrderPublisher(
    orderId: OrderId,
    submissionActor: ActorRef,
    listener: OrderPublisher.Listener,
    policy: SubmissionPolicy = new SubmissionPolicyImpl)(implicit context: ActorContext) {

  private implicit val sender = context.self
  private var lastSubmissionMessage: Any = StopSubmitting(orderId)

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
    if (lastSubmissionMessage != message) {
      lastSubmissionMessage = message
      submissionActor ! message
    }
  }

  val topics: Seq[String] = Seq(FiatBalanceChanged.Topic, BitcoinBalanceChanged.Topic)

  val receiveSubmissionEvents: Actor.Receive = {
    case FiatBalanceChanged(balance) =>
      policy.setFiatBalances(balance)
      updateSubmissionRequest()

    case BitcoinBalanceChanged(balance) =>
      policy.setBitcoinBalance(balance)
      updateSubmissionRequest()

    case InMarket(entryInMarket) if policy.entry.contains(entryInMarket) =>
      listener.inMarket()

    case Offline(entryInMarket) if policy.entry.contains(entryInMarket) =>
      listener.offline()
  }
}

private[orders] object OrderPublisher {
  trait Listener {
    def inMarket(): Unit
    def offline(): Unit
  }
}
