package coinffeine.peer.amounts

import coinffeine.model.bitcoin.TransactionSizeFeeCalculator
import coinffeine.model.payment.OkPayPaymentProcessor

trait DefaultAmountsComponent extends AmountsComponent {
  override lazy val exchangeAmountsCalculator =
    new DefaultAmountsCalculator(OkPayPaymentProcessor, TransactionSizeFeeCalculator)
}
