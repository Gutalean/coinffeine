package coinffeine.peer.exchange.handshake

import akka.actor._
import akka.pattern._
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.FutureMatchers
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.payment.PaymentProcessorActor

class AccountIdValidatorImplTest extends AkkaSpec with FutureMatchers {

  /** Auxiliary actor to execute the validator within */
  private class TestValidatorActor(validator: AccountIdValidator) extends Actor {
    import context.dispatcher

    override def receive: Receive = {
      case accountId: AccountId => validator.validate(accountId).pipeTo(sender())
    }
  }

  "An account id validator" should "consider existing accounts as valid" in new Fixture {
    actor ! "existingId"
    paymentProcessor.expectMsg(PaymentProcessorActor.CheckAccountExistence("existingId"))
    paymentProcessor.reply(PaymentProcessorActor.AccountExistence.Existing)
    expectMsg(AccountIdValidator.Valid)
  }

  it should "consider non existing accounts as invalid" in new Fixture {
    actor ! "unknownId"
    paymentProcessor.expectMsg(PaymentProcessorActor.CheckAccountExistence("unknownId"))
    paymentProcessor.reply(PaymentProcessorActor.AccountExistence.NonExisting)
    expectMsg(AccountIdValidator.Invalid)
  }

  it should "consider invalid accounts whose existence cannot be checked" in new Fixture {
    actor ! "existingId"
    paymentProcessor.expectMsg(PaymentProcessorActor.CheckAccountExistence("existingId"))
    paymentProcessor.reply(PaymentProcessorActor.AccountExistence.CannotCheck)
    expectMsg(AccountIdValidator.Invalid)
  }

  trait Fixture {
    protected val paymentProcessor = TestProbe()
    private val validator = new AccountIdValidatorImpl(paymentProcessor.ref)
    protected val actor = system.actorOf(Props(new TestValidatorActor(validator)))
  }
}
