package coinffeine.peer.exchange.broadcast

import coinffeine.model.bitcoin.ImmutableTransaction

/** Keep the transactions related to an exchange and determine what should be broadcast */
private class BroadcastPolicy(refund: ImmutableTransaction, refundSafetyBlockCount: Int) {

  private var lastOffer: Option[ImmutableTransaction] = None
  private var publicationRequested: Boolean = false
  private var height: Long = 0L

  private val refundBlock = refund.get.getLockTime
  private val panicBlock = refundBlock - refundSafetyBlockCount
  def relevantBlocks: Seq[Long] = Seq(panicBlock, refundBlock)

  def addOfferTransaction(tx: ImmutableTransaction): Unit = {
    lastOffer = Some(tx)
  }

  def requestPublication(): Unit = { publicationRequested = true }

  def updateHeight(currentHeight: Long): Unit = { height = currentHeight }

  def bestTransaction: ImmutableTransaction = lastOffer getOrElse refund

  def shouldBroadcast: Boolean = shouldBroadcastLastOffer || canBroadcastRefund

  private def shouldBroadcastLastOffer = lastOffer.isDefined && (panicked || publicationRequested)
  private def canBroadcastRefund = height >= refundBlock
  private def panicked = height >= panicBlock
}
