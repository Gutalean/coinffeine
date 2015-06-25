package coinffeine.peer.exchange.micropayment

import akka.persistence.PersistentActor

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.RunningExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.Step
import coinffeine.protocol.gateway.MessageGateway.ForwardMessage
import coinffeine.protocol.messages.PublicMessage

private[micropayment] abstract class BaseChannelActor(
    exchange: RunningExchange,
    collaborators: MicroPaymentChannelActor.Collaborators) extends PersistentActor {

  protected def forwardToCounterpart(message: PublicMessage): Unit = {
    collaborators.gateway ! ForwardMessage(message, exchange.counterpartId)
  }

  protected def notifyCompletedStep(step: Step): Unit = {
    notifyListeners(ExchangeUpdate(exchange.completeStep(step.value)))
  }

  protected def notifyListeners(message: Any): Unit = {
    collaborators.resultListeners.foreach { _ ! message }
  }
}
