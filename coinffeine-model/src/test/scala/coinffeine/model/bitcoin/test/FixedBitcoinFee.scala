package coinffeine.model.bitcoin.test

import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.model.currency._

class FixedBitcoinFee(fee: BitcoinAmount) extends BitcoinFeeCalculator {
  override val defaultTransactionFee: BitcoinAmount = fee
}
