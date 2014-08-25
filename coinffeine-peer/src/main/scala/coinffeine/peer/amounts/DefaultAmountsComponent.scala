package coinffeine.peer.amounts

import coinffeine.model.payment.OkPayPaymentProcessor

trait DefaultAmountsComponent extends AmountsComponent {
  override lazy val exchangeAmountsCalculator =
    new DefaultExchangeAmountsCalculator(OkPayPaymentProcessor)
}
