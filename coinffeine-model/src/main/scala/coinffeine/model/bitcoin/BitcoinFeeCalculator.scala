package coinffeine.model.bitcoin

import coinffeine.model.currency.Bitcoin

trait BitcoinFeeCalculator {
  def defaultTransactionFee: Bitcoin.Amount
}
