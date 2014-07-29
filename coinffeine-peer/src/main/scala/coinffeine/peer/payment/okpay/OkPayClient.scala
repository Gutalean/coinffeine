package coinffeine.peer.payment.okpay

import scala.concurrent.{ExecutionContext, Future}

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor.{AccountId, PaymentId}
import coinffeine.model.payment.{AnyPayment, Payment}
import coinffeine.peer.payment.PaymentProcessorException

trait OkPayClient {

  val accountId: AccountId

  def sendPayment[C <: FiatCurrency](to: AccountId, amount: CurrencyAmount[C],
                                     comment: String): Future[Payment[C]]

  def findPayment(paymentId: PaymentId): Future[Option[AnyPayment]]

  def currentBalances(): Future[Seq[FiatAmount]]

  def currentBalance[C <: FiatCurrency](currency: C): Future[CurrencyAmount[C]] = {
    implicit val ec = executionContext
    currentBalances().map { balances =>
      balances.find(_.currency == currency)
        .getOrElse(throw new PaymentProcessorException(s"No balance in $currency"))
        .asInstanceOf[CurrencyAmount[C]]
    }
  }

  protected def executionContext: ExecutionContext
}
