package coinffeine.peer.amounts

import coinffeine.model.bitcoin.TransactionSizeFeeCalculator
import coinffeine.model.payment.OkPayPaymentProcessor

trait DefaultAmountsComponent extends AmountsComponent {
  override lazy val stepwisePaymentCalculator =
    new DefaultStepwisePaymentCalculator(OkPayPaymentProcessor)

  override lazy val bitcoinFeeCalculator = TransactionSizeFeeCalculator

  override lazy val amountsCalculator =
    new DefaultAmountsCalculator(stepwisePaymentCalculator, bitcoinFeeCalculator)
}
