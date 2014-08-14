package coinffeine.peer.exchange.micropayment

import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.StartMicroPaymentChannel
import coinffeine.peer.exchange.util.MessageForwarding

private[micropayment] abstract class InitializedChannelBehavior[C <: FiatCurrency](
    init: StartMicroPaymentChannel[C]) {
  import init._

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
