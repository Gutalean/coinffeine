package com.coinffeine.client.exchange

import coinffeine.model.exchange.Exchange
import com.coinffeine.common.exchange.MicroPaymentChannel.IntermediateStep

/** Payment description formatter */
object PaymentDescription {
  def apply(exchangeId: Exchange.Id, step: IntermediateStep): String =
    s"Payment for $exchangeId, step ${step.value}"
}
