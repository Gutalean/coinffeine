package coinffeine.peer.exchange.broadcast

import coinffeine.model.bitcoin.ImmutableTransaction

private trait BroadcastPolicy {
  def addOfferTransaction(tx: ImmutableTransaction): Unit
  def invalidateOffers(): Unit
  def requestPublication(): Unit
  def updateHeight(currentHeight: Long): Unit
  def bestTransaction: ImmutableTransaction
  def shouldBroadcast: Boolean
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
}

/** Keep the transactions related to an exchange and determine what should be broadcast */
private class BroadcastPolicyImpl(refund: ImmutableTransaction, refundSafetyBlockCount: Int) extends BroadcastPolicy {

  private var offer: BroadcastPolicy.Offer = BroadcastPolicy.NoOffer
  private var publicationRequested: Boolean = false
  private var height: Long = 0L

  private val refundBlock = refund.get.getLockTime
  private val panicBlock = refundBlock - refundSafetyBlockCount

  override def addOfferTransaction(tx: ImmutableTransaction): Unit = {
    offer = offer.add(tx)
  }

  override def invalidateOffers(): Unit = {
    offer = offer.invalidate
  }

  override def requestPublication(): Unit = { publicationRequested = true }

  override def updateHeight(currentHeight: Long): Unit = { height = currentHeight }

  override def bestTransaction: ImmutableTransaction = offer.best getOrElse refund

  override def shouldBroadcast: Boolean = shouldBroadcastLastOffer || canBroadcastRefund

  private def shouldBroadcastLastOffer = offer.best.isDefined && (panicked || publicationRequested)
  private def canBroadcastRefund = height >= refundBlock
  private def panicked = height >= panicBlock
}
