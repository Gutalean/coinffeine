package coinffeine.peer.payment.okpay

import scala.concurrent.Future

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor.{AccountId, PaymentId}
import coinffeine.peer.payment.AnyPayment

trait OkPayClient {

  val accountId: AccountId

  def sendPayment[C <: FiatCurrency](to: AccountId, amount: CurrencyAmount[C],
                                     comment: String): Future[Payment[C]]

  def findPayment(paymentId: PaymentId): Future[Option[AnyPayment]]

  def currentBalance[C <: FiatCurrency](currency: C): Future[CurrencyAmount[C]]
}
