package coinffeine.model.bitcoin

import coinffeine.model.currency._

/** Computes fees based on the size of the transactions. */
object TransactionSizeFeeCalculator extends BitcoinFeeCalculator {

  override val defaultTransactionFee = 0.0001.BTC
}
