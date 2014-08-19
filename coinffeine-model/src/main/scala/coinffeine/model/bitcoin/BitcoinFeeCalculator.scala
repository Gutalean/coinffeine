package coinffeine.model.bitcoin

import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Implicits._

object BitcoinFeeCalculator {

  def calculateFee(amount: BitcoinAmount): BitcoinAmount = 0.0001.BTC

  def amountPlusFee(amount: BitcoinAmount): BitcoinAmount =
    amount + calculateFee(amount)
}
