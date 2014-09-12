package coinffeine.peer.exchange.micropayment

import scala.concurrent.ExecutionContext

import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.StartMicroPaymentChannel
import coinffeine.peer.exchange.util.MessageForwarding

private[micropayment] abstract class InitializedChannelBehavior[C <: FiatCurrency](
    init: StartMicroPaymentChannel[C])(implicit val executor: ExecutionContext) {
  import init._

  protected val forwarding = new MessageForwarding(init.gateway, exchange.counterpartId)

  protected def reportProgress(signatures: Int, payments: Int): Unit = {
    val progressUpdate = exchange.increaseProgress(
      btcAmount =
        if (signatures == 0) Bitcoin.Zero
        else exchange.amounts.steps(signatures - 1).progress.bitcoinsTransferred,
      fiatAmount =
        if (payments == 0) CurrencyAmount.zero(exchange.currency)
        else exchange.amounts.steps(payments - 1).progress.fiatTransferred
    )
    resultListeners.foreach { _ ! ExchangeProgress(progressUpdate) }
  }
}
