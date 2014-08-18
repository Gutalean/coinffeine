package coinffeine.peer.exchange.micropayment

import scala.concurrent.ExecutionContext

import akka.actor.Actor

import coinffeine.common.akka.ServiceRegistry
import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.StartMicroPaymentChannel
import coinffeine.peer.exchange.util.MessageForwarding
import coinffeine.protocol.gateway.MessageGateway

private[micropayment] abstract class InitializedChannelBehavior[C <: FiatCurrency](
    init: StartMicroPaymentChannel[C])(implicit val executor: ExecutionContext) {
  import init._

  protected val messageGateway = new ServiceRegistry(registry)
    .eventuallyLocate(MessageGateway.ServiceId)
  protected val forwarding =
    new MessageForwarding(messageGateway, exchange.counterpartId, exchange.brokerId)

  protected def reportProgress(signatures: Int, payments: Int): Unit = {
    val progressUpdate = exchange.increaseProgress(
      btcAmount = exchange.amounts.stepBitcoinAmount * signatures,
      fiatAmount = exchange.amounts.stepFiatAmount * payments
    )
    resultListeners.foreach { _ ! ExchangeProgress(progressUpdate) }
  }
}
