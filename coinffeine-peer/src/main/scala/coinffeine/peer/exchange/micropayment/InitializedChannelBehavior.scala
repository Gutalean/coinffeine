package coinffeine.peer.exchange.micropayment

import scala.concurrent.ExecutionContext

import coinffeine.common.akka.ServiceRegistry
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.StartMicroPaymentChannel
import coinffeine.peer.exchange.util.MessageForwarding
import coinffeine.protocol.gateway.MessageGateway

private[micropayment] abstract class InitializedChannelBehavior[C <: FiatCurrency](
    init: StartMicroPaymentChannel[C])(implicit val executor: ExecutionContext) {
  import init._

  protected val messageGateway = new ServiceRegistry(registry)
    .eventuallyLocate(MessageGateway.ServiceId)
  protected val forwarding = new MessageForwarding(messageGateway, exchange.counterpartId)

  protected def reportProgress(signatures: Int, payments: Int): Unit = {
    val progressUpdate = exchange.increaseProgress(
      btcAmount = stepsUntil(signatures).foldLeft(Bitcoin.Zero)(_ + _.bitcoinAmount),
      fiatAmount = stepsUntil(payments)
        .foldLeft[CurrencyAmount[C]](CurrencyAmount.zero(exchange.currency))(_ + _.fiatAmount)
    )
    resultListeners.foreach { _ ! ExchangeProgress(progressUpdate) }
  }

  private def stepsUntil(step: Int) = exchange.amounts.steps.take(step)
}
