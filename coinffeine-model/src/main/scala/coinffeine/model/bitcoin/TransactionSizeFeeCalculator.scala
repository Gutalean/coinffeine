package coinffeine.model.bitcoin

import coinffeine.model.currency.Implicits._

/** Computes fees based on the size of the transactions. */
object TransactionSizeFeeCalculator extends BitcoinFeeCalculator {
  private val DefaultTransactionBytes = 80
  private val DefaultTransactionFee = 0.0001.BTC

  override val defaultTransactionFee = DefaultTransactionFee * DefaultTransactionBytes
}
