package coinffeine.peer.exchange.handshake

import akka.actor.{ActorContext, ActorRef}

import coinffeine.common.akka.AskPattern
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.AccountExistence

/** Delegates validation to a payment processor actor */
class AccountIdValidatorImpl(paymentProcessor: ActorRef) extends AccountIdValidator {

  override def validate(accountId: String)(implicit context: ActorContext) = {
    import context.dispatcher
    checkExistence(accountId, context).map(acceptExistingAccounts)
  }

  private def checkExistence(accountId: String, context: ActorContext) = {
    import context.dispatcher
    AskPattern(
      to = paymentProcessor,
      request = PaymentProcessorActor.CheckAccountExistence(accountId)
    ).withImmediateReply[AccountExistence]
  }

  private def acceptExistingAccounts(existence: AccountExistence) = existence match {
    case AccountExistence.Existing => AccountIdValidator.Valid
    case AccountExistence.NonExisting | AccountExistence.CannotCheck =>
      AccountIdValidator.Invalid
  }
}
