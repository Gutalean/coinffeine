package coinffeine.peer.api

import scala.concurrent.Future

import coinffeine.model.currency.Euro
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.payment.PaymentProcessorProperties
import coinffeine.peer.payment.okpay.OkPayApiCredentials

/** Represents how the app interact with a payment processor */
trait CoinffeinePaymentProcessor extends PaymentProcessorProperties {
  import CoinffeinePaymentProcessor._

  def accountId: Option[PaymentProcessor.AccountId]

  /** Get the current balance if possible */
  def currentBalance(): Option[CoinffeinePaymentProcessor.Balance]

  /** Request the payment processor to refresh the balances. */
  def refreshBalances(): Unit

  /** Check if the given credentials can be used to access the API. */
  def testCredentials(credentials: OkPayApiCredentials): Future[TestResult] = ???
}

object CoinffeinePaymentProcessor {

  sealed trait TestResult
  object TestResult {
    case object Valid extends TestResult
    case object Invalid extends TestResult
    case object CannotConnect extends TestResult
  }

  case class Balance(totalFunds: Euro.Amount, blockedFunds: Euro.Amount = Euro.zero) {
    val availableFunds = totalFunds - blockedFunds
  }
}
