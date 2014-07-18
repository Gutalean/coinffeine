package coinffeine.peer.api.impl

import akka.actor.ActorRef
import akka.pattern._

import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.CurrencyAmount
import coinffeine.peer.api.CoinffeinePaymentProcessor
import coinffeine.peer.payment.PaymentProcessor.{BalanceRetrieved, RetrieveBalance}

private[impl] class DefaultCoinffeinePaymentProcessor(override val peer: ActorRef)
  extends CoinffeinePaymentProcessor with PeerActorWrapper {

  override def currentBalance(): CurrencyAmount[Euro.type] =
    await((peer ? RetrieveBalance(Euro)).mapTo[BalanceRetrieved[Euro.type]]).balance
}
