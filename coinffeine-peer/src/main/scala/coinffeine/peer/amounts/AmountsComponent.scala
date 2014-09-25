package coinffeine.peer.amounts

import coinffeine.model.bitcoin.BitcoinFeeCalculator

trait AmountsComponent {
  def bitcoinFeeCalculator: BitcoinFeeCalculator
  def stepwisePaymentCalculator: StepwisePaymentCalculator
  def amountsCalculator: AmountsCalculator
}
