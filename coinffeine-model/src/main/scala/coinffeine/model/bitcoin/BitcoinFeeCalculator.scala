package coinffeine.model.bitcoin

import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Implicits._

object BitcoinFeeCalculator {

  private val defaultTransactionBytes = 80
  private val defaultTransactionFee = 0.0001.BTC

  def calculateFee(transactionBytes: Int = defaultTransactionBytes): BitcoinAmount =
    defaultTransactionFee * transactionBytes

  def amountPlusFee(amount: BitcoinAmount,
                    transactionBytes: Int = defaultTransactionBytes): BitcoinAmount =
    amount + calculateFee(transactionBytes)
}
