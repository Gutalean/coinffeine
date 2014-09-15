package coinffeine.peer.market.orders

import akka.actor.{Actor, ActorContext, ActorRef}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.OrderBookEntry
import coinffeine.peer.market.SubmissionSupervisor._
import coinffeine.peer.market.orders.controller.OrderPublication
import coinffeine.peer.market.orders.controller.OrderPublication.Listener

private[orders] class DelegatedPublication[C <: FiatCurrency](
    entry: OrderBookEntry[C], submissionActor: ActorRef)(implicit context: ActorContext)
  extends OrderPublication[C] {

  private var _inMarket = false
  private var publicationListeners = Seq.empty[OrderPublication.Listener]

  override def isInMarket = _inMarket

  override def keepPublishing(): Unit = {
    submissionActor ! KeepSubmitting(entry)
  }

  override def stopPublishing(): Unit = {
    submissionActor ! StopSubmitting(entry.id)
  }

  override def addListener(listener: Listener): Unit = {
    publicationListeners :+= listener
  }

  val receiveSubmissionEvents: Actor.Receive = {
    case InMarket(`entry`) =>
      _inMarket = true
      publicationListeners.foreach(_.inMarket())

    case Offline(`entry`) =>
      _inMarket = false
      publicationListeners.foreach(_.offline())
  }
}
