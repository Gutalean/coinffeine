package coinffeine.model.bitcoin.test

import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.model.currency.Bitcoin

class FixedBitcoinFee(fee: Bitcoin.Amount) extends BitcoinFeeCalculator {
  override val defaultTransactionFee: Bitcoin.Amount = fee
}
