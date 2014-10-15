package coinffeine.peer.exchange.broadcast

import coinffeine.model.bitcoin.ImmutableTransaction

/** Keep the transactions related to an exchange and determine what should be broadcast */
private class ExchangeTransactions(refund: ImmutableTransaction) {

  private var lastOffer: Option[ImmutableTransaction] = None

  def addOfferTransaction(tx: ImmutableTransaction): Unit = {
    lastOffer = Some(tx)
  }

  def bestTransaction: ImmutableTransaction = lastOffer getOrElse refund
}
