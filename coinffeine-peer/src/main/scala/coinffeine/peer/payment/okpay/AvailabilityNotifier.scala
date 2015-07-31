package coinffeine.peer.payment.okpay

import akka.event.EventStream
import com.typesafe.scalalogging.StrictLogging

import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.okpay.blocking.AvailabilityListener

private class AvailabilityNotifier(eventStream: EventStream)
    extends AvailabilityListener with StrictLogging {

  override def onAvailable(funds: ExchangeId): Unit = {
    logger.debug("{} fiat funds becomes available", funds)
    eventStream.publish(PaymentProcessorActor.AvailableFunds(funds))
  }

  override def onUnavailable(funds: ExchangeId): Unit = {
    logger.debug("{} fiat funds becomes unavailable", funds)
    eventStream.publish(PaymentProcessorActor.UnavailableFunds(funds))
  }
}
