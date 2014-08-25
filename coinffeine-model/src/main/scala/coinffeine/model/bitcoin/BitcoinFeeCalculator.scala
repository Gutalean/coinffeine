package coinffeine.model.bitcoin

import coinffeine.model.currency.BitcoinAmount

trait BitcoinFeeCalculator {
  def defaultTransactionFee: BitcoinAmount
}
