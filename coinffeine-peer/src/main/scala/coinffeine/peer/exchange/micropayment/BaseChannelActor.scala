package coinffeine.peer.exchange.micropayment

import akka.actor.Actor

import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.RunningExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.util.MessageForwarding

private[micropayment] abstract class BaseChannelActor[C <: FiatCurrency](
    exchange: RunningExchange[C],
    collaborators: MicroPaymentChannelActor.Collaborators) extends Actor {

  protected val forwarding = new MessageForwarding(collaborators.gateway, exchange.counterpartId)

  protected def reportProgress(signatures: Int, payments: Int): Unit = {
    val progressUpdate = exchange.increaseProgress(
      btcAmount =
        if (signatures == 0) Bitcoin.Zero
        else exchange.amounts.steps(signatures - 1).progress.bitcoinsTransferred,
      fiatAmount =
        if (payments == 0) CurrencyAmount.zero(exchange.currency)
        else exchange.amounts.steps(payments - 1).progress.fiatTransferred
    )
    collaborators.resultListeners.foreach { _ ! ExchangeUpdate(progressUpdate) }
  }
}
