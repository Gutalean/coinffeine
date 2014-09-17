package coinffeine.peer.market.orders

import akka.actor.{Actor, ActorContext, ActorRef}

import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.market._
import coinffeine.peer.market.SubmissionSupervisor._
import coinffeine.peer.market.orders.controller.OrderPublication
import coinffeine.peer.market.orders.controller.OrderPublication.Listener

private[orders] class DelegatedPublication[C <: FiatCurrency](
    id: OrderId, orderType: OrderType, price: Price[C],
    submissionActor: ActorRef)(implicit context: ActorContext) extends OrderPublication[C] {

  private implicit val sender = context.self
  private var entry: OrderBookEntry[C] = _
  private var _inMarket = false
  private var publicationListeners = Seq.empty[OrderPublication.Listener]

  override def isInMarket = _inMarket

  override def keepPublishing(pendingAmount: BitcoinAmount): Unit = {
    entry = OrderBookEntry(id, orderType, pendingAmount, price)
    submissionActor ! KeepSubmitting(entry)
  }

  override def stopPublishing(): Unit = {
    _inMarket = false
    submissionActor ! StopSubmitting(entry.id)
  }

  override def addListener(listener: Listener): Unit = {
    publicationListeners :+= listener
  }

  val receiveSubmissionEvents: Actor.Receive = {
    case InMarket(entryInMarket) if entryInMarket == entry =>
      _inMarket = true
      publicationListeners.foreach(_.inMarket())

    case Offline(entryInMarket) if entryInMarket == entry =>
      _inMarket = false
      publicationListeners.foreach(_.offline())
  }
}
