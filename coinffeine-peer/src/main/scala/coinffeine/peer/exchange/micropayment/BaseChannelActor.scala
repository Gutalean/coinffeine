package coinffeine.peer.exchange.micropayment

import akka.persistence.PersistentActor

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.RunningExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.Step
import coinffeine.peer.exchange.util.MessageForwarding

private[micropayment] abstract class BaseChannelActor[C <: FiatCurrency](
    exchange: RunningExchange[C],
    collaborators: MicroPaymentChannelActor.Collaborators) extends PersistentActor {

  override def persistenceId: String = s"micropayment-channel-${exchange.id.value}"

  protected val forwarding = new MessageForwarding(collaborators.gateway, exchange.counterpartId)

  protected def notifyCompletedStep(step: Step): Unit = {
    notifyListeners(ExchangeUpdate(exchange.completeStep(step.value)))
  }

  protected def notifyListeners(message: Any): Unit = {
    collaborators.resultListeners.foreach { _ ! message }
  }
}
