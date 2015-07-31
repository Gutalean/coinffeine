package coinffeine.peer.exchange.broadcast

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.exchange.broadcast.BroadcastPolicy.{RefundPublicationTrigger, OfferPublicationTrigger}

private trait BroadcastPolicy {
  def addOfferTransaction(tx: ImmutableTransaction): Unit
  def invalidateOffers(): Unit
  def requestPublication(): Unit
  def updateHeight(currentHeight: Long): Unit
  def transactionToBroadcast: Option[ImmutableTransaction]
}

private object BroadcastPolicy {

  sealed trait Offer {
    def add(offer: ImmutableTransaction): Offer
    def invalidate: Offer
    def best: Option[ImmutableTransaction]
  }

  case object NoOffer extends Offer {
    override def add(offer: ImmutableTransaction) = BestOffer(offer)
    override def invalidate = InvalidatedOffers
    override def best = None
  }

  case class BestOffer(offer: ImmutableTransaction) extends Offer {
    override def add(betterOffer: ImmutableTransaction) = BestOffer(betterOffer)
    override def invalidate = InvalidatedOffers
    override def best = Some(offer)
  }

  case object InvalidatedOffers extends Offer {
    override def add(offer: ImmutableTransaction) = this
    override def invalidate = this
    override def best = None
  }

  sealed trait OfferPublicationTrigger {
    def shouldPublish: Boolean
    def publicationRequested: OfferPublicationTrigger
    def heightReached(currentHeight: Long): OfferPublicationTrigger
  }

  object OfferPublicationTrigger {
    case class WaitingForHeight(panicHeight: Long) extends OfferPublicationTrigger {
      override val shouldPublish = false
      override def publicationRequested = PublishImmediately
      override def heightReached(currentHeight: Long) = if (currentHeight >= panicHeight) PublishImmediately else this
    }

    case object PublishImmediately extends OfferPublicationTrigger {
      override val shouldPublish = true
      override def publicationRequested = this
      override def heightReached(currentHeight: Long) = this
    }
  }

  sealed trait RefundPublicationTrigger {
    def shouldPublish: Boolean
    def heightReached(currentHeight: Long): RefundPublicationTrigger
  }

  object RefundPublicationTrigger {
    case class WaitingForHeight(refundHeight: Long) extends RefundPublicationTrigger {
      override val shouldPublish = false
      override def heightReached(currentHeight: Long) = if (currentHeight >= refundHeight) PublishImmediately else this
    }

    case object PublishImmediately extends RefundPublicationTrigger {
      override val shouldPublish = true
      override def heightReached(currentHeight: Long) = this
    }
  }
}

/** Keep the transactions related to an exchange and determine what should be broadcast */
private class BroadcastPolicyImpl(refund: ImmutableTransaction, refundSafetyBlockCount: Int) extends BroadcastPolicy {

  private val refundBlock = refund.get.getLockTime
  private val panicBlock = refundBlock - refundSafetyBlockCount

  private var offer: BroadcastPolicy.Offer = BroadcastPolicy.NoOffer
  private var offerPublicationTrigger: OfferPublicationTrigger = OfferPublicationTrigger.WaitingForHeight(panicBlock)
  private var refundPublicationTrigger: RefundPublicationTrigger = RefundPublicationTrigger.WaitingForHeight(refundBlock)

  override def addOfferTransaction(tx: ImmutableTransaction): Unit = {
    offer = offer.add(tx)
  }

  override def invalidateOffers(): Unit = {
    offer = offer.invalidate
  }

  override def requestPublication(): Unit = { 
    offerPublicationTrigger = offerPublicationTrigger.publicationRequested
  }

  override def updateHeight(currentHeight: Long): Unit = {
    refundPublicationTrigger = refundPublicationTrigger.heightReached(currentHeight)
    offerPublicationTrigger = offerPublicationTrigger.heightReached(currentHeight)
  }

  override def transactionToBroadcast = bestOfferToBroadcast orElse refundToBroadcast

  private def bestOfferToBroadcast = for {
    tx <- offer.best if offerPublicationTrigger.shouldPublish
  } yield tx

  private def refundToBroadcast = if (refundPublicationTrigger.shouldPublish) Some(refund) else None
}
