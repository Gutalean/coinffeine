package coinffeine.model.bitcoin

import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Implicits._

object BitcoinFeeCalculator {

  private val defaultTransactionBytes = 80

  def calculateFee(transactionBytes: Int = defaultTransactionBytes): BitcoinAmount =
    0.0001.BTC * transactionBytes

  def amountPlusFee(amount: BitcoinAmount,
                    transactionBytes: Int = defaultTransactionBytes): BitcoinAmount =
    amount + calculateFee(transactionBytes)
}
