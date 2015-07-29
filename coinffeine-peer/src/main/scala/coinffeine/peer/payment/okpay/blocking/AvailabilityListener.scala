package coinffeine.peer.payment.okpay.blocking

import coinffeine.model.exchange.ExchangeId

trait AvailabilityListener {
  def onAvailable(funds: ExchangeId): Unit
  def onUnavailable(funds: ExchangeId): Unit
}
