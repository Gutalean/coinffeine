package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.ActorRef
import akka.pattern._
import org.slf4j.LoggerFactory

import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.{Balance, FiatCurrency, CurrencyAmount}
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.model.properties.PropertyMap
import coinffeine.peer.api.CoinffeinePaymentProcessor
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment.PaymentProcessorProperties

private[impl] class DefaultCoinffeinePaymentProcessor(
    override val accountId: AccountId,
    override val peer: ActorRef,
    properties: PaymentProcessorProperties) extends CoinffeinePaymentProcessor with PeerActorWrapper {
  import DefaultCoinffeinePaymentProcessor._

  override val balance = properties.balance

  override def currentBalance(): Option[CoinffeinePaymentProcessor.Balance] =
    await((peer ? RetrieveBalance(Euro)).mapTo[RetrieveBalanceResponse].map {
      case BalanceRetrieved(
        balance @ CurrencyAmount(_, Euro),
        blockedFunds @ CurrencyAmount(_, Euro)) =>
        Some(CoinffeinePaymentProcessor.Balance(
          balance.asInstanceOf[CurrencyAmount[Euro.type]],
          blockedFunds.asInstanceOf[CurrencyAmount[Euro.type]]
        ))
      case nonEurBalance @ BalanceRetrieved(_, _) =>
        Log.error("Balance not in euro: {}", nonEurBalance)
        None
      case BalanceRetrievalFailed(_, cause) =>
        Log.error("Cannot retrieve current balance", cause)
        None
    })
}

private object DefaultCoinffeinePaymentProcessor {
  val Log = LoggerFactory.getLogger(classOf[DefaultCoinffeinePaymentProcessor])
}
