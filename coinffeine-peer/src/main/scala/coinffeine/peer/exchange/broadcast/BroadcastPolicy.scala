package coinffeine.peer.exchange.broadcast

import coinffeine.model.bitcoin.ImmutableTransaction

private trait BroadcastPolicy {
  def addOfferTransaction(tx: ImmutableTransaction): Unit
  def unsetLastOffer(): Unit
  def requestPublication(): Unit
  def updateHeight(currentHeight: Long): Unit
  def bestTransaction: ImmutableTransaction
  def shouldBroadcast: Boolean
}

/** Keep the transactions related to an exchange and determine what should be broadcast */
private class BroadcastPolicyImpl(refund: ImmutableTransaction, refundSafetyBlockCount: Int) extends BroadcastPolicy {

  private var lastOffer: Option[ImmutableTransaction] = None
  private var publicationRequested: Boolean = false
  private var height: Long = 0L

  private val refundBlock = refund.get.getLockTime
  private val panicBlock = refundBlock - refundSafetyBlockCount

  override def addOfferTransaction(tx: ImmutableTransaction): Unit = {
    lastOffer = Some(tx)
  }

  override def unsetLastOffer(): Unit = {
    lastOffer = None
  }

  override def requestPublication(): Unit = { publicationRequested = true }

  override def updateHeight(currentHeight: Long): Unit = { height = currentHeight }

  override def bestTransaction: ImmutableTransaction = lastOffer getOrElse refund

  override def shouldBroadcast: Boolean = shouldBroadcastLastOffer || canBroadcastRefund

  private def shouldBroadcastLastOffer = lastOffer.isDefined && (panicked || publicationRequested)
  private def canBroadcastRefund = height >= refundBlock
  private def panicked = height >= panicBlock
}
