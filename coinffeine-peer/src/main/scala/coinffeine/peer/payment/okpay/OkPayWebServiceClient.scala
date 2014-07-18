package coinffeine.peer.payment.okpay

import java.util.{Currency => JavaCurrency}
import scala.concurrent.Future
import scalaxb.{DispatchHttpClientsAsync, Soap11ClientsAsync}

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor.{PaymentId, AccountId}
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.generated._

private[okpay] class OkPayWebServiceClient(account: String, tokenGenerator: TokenGenerator)
  extends OkPayClient
  with BasicHttpBinding_I_OkPayAPIBindings
  with Soap11ClientsAsync
  with DispatchHttpClientsAsync {

  import coinffeine.peer.payment.okpay.OkPayWebServiceClient._

  override def sendPayment[C <: FiatCurrency](
      to: AccountId, amount: CurrencyAmount[C], comment: String): Future[Payment[C]] =
    service.send_Money(
      walletID = Some(Some(account)),
      securityToken = Some(Some(buildCurrentToken())),
      receiver = Some(Some(to)),
      currency = Some(Some(amount.currency.javaCurrency.getCurrencyCode)),
      amount = Some(amount.value),
      comment = Some(Some(comment)),
      isReceiverPaysFees = Some(false),
      invoice = None
    ).map { response =>
      parsePaymentOfCurrency(response.Send_MoneyResult.flatten.get, amount.currency)
    }

  override def findPayment(paymentId: PaymentId): Future[Option[AnyPayment]] =
    service.transaction_Get(
      walletID = Some(Some(account)),
      securityToken = Some(Some(buildCurrentToken())),
      txnID = Some(paymentId.toLong),
      invoice = None
    ).map { result =>
      result.Transaction_GetResult.flatten.map(parsePayment)
    }

  override def currentBalance[C <: FiatCurrency](currency: C): Future[CurrencyAmount[C]] = {
    service.wallet_Get_Balance(
      walletID = Some(Some(this.account)),
      securityToken = Some(Some(buildCurrentToken()))
    ).map { response =>
      response.Wallet_Get_BalanceResult.flatten.map(b => parseArrayOfBalance(b, currency))
        .getOrElse(throw new PaymentProcessorException("Cannot parse balances: " + response))
    }
  }

  private def parsePaymentOfCurrency[C <: FiatCurrency](
     txInfo: TransactionInfo, expectedCurrency: C): Payment[C] = {
    val payment = parsePayment(txInfo)
    if (payment.amount.currency != expectedCurrency) {
      throw new PaymentProcessorException(
        s"payment is expressed in ${payment.amount.currency}, but $expectedCurrency was expected")
    }
    payment.asInstanceOf[Payment[C]]
  }

  private def parsePayment(txInfo: TransactionInfo): AnyPayment = {
    txInfo match {
      case TransactionInfo(
          Some(amount),
          Flatten(description),
          Flatten(rawCurrency),
          Flatten(rawDate),
          _,
          Some(paymentId),
          _,
          Some(net),
          _,
          Flatten(WalletId(receiverId)),
          Flatten(WalletId(senderId)),
          _) =>
        val currency = FiatCurrency(JavaCurrency.getInstance(txInfo.Currency.get.get))
        val amount = currency.amount(net)
        val date = DateFormat.parseDateTime(rawDate)
        Payment(paymentId.toString, senderId, receiverId, amount, date, description)

      case _ => throw new PaymentProcessorException(s"Cannot parse the sent payment: $txInfo")
    }
  }

  private def parseArrayOfBalance[C <: FiatCurrency](
      balances: ArrayOfBalance, expectedCurrency: C): CurrencyAmount[C] = {
    val amounts = balances.Balance.collect {
      case Some(Balance(a, c)) if c.get.get == expectedCurrency.javaCurrency.getCurrencyCode => a.get
    }
    expectedCurrency.amount(amounts.sum)
  }

  private def buildCurrentToken() = tokenGenerator.build(DateTime.now(DateTimeZone.UTC))
}

private[okpay] object OkPayWebServiceClient {

  val DateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  private object WalletId {
    def unapply(info: AccountInfo): Option[String] = info.WalletID.flatten
  }

  private object Flatten {
    def unapply[T](option: Option[Option[T]]): Option[T] = option.flatten
  }
}
