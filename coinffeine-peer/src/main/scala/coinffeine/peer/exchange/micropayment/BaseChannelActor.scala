package coinffeine.peer.exchange.micropayment

import akka.actor.Actor

import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange.{Both, RunningExchange}
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.util.MessageForwarding

private[micropayment] abstract class BaseChannelActor[C <: FiatCurrency](
    exchange: RunningExchange[C],
    collaborators: MicroPaymentChannelActor.Collaborators) extends Actor {

  protected val forwarding = new MessageForwarding(collaborators.gateway, exchange.counterpartId)

  protected def reportProgress(signatures: Int): Unit = {
    val progressUpdate = exchange.increaseProgress(
      if (signatures == 0) Both.fill(Bitcoin.Zero)
      else exchange.amounts.steps(signatures - 1).progress.bitcoinsTransferred
    )
    collaborators.resultListeners.foreach { _ ! ExchangeUpdate(progressUpdate) }
  }
}
