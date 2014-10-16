package coinffeine.peer.exchange.micropayment

import akka.actor.Actor

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.RunningExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.Step
import coinffeine.peer.exchange.util.MessageForwarding

private[micropayment] abstract class BaseChannelActor[C <: FiatCurrency](
    exchange: RunningExchange[C],
    collaborators: MicroPaymentChannelActor.Collaborators) extends Actor {

  protected val forwarding = new MessageForwarding(collaborators.gateway, exchange.counterpartId)

  protected def reportProgress(step: Step): Unit = {
    val progress = step.select(exchange.amounts).progress
    val progressUpdate = exchange.increaseProgress(progress.bitcoinsTransferred)
    collaborators.resultListeners.foreach { _ ! ExchangeUpdate(progressUpdate) }
  }
}
