package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scalaxb.Soap11Fault

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import soapenvelope11.Fault

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor.{AccountId, PaymentId}
import coinffeine.model.payment.{AnyPayment, Payment}
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.OkPayClient.{FeePolicy, PaidByReceiver, PaidBySender}
import coinffeine.peer.payment.okpay.generated._

/** SOAP client of OKPay service.
  *
  * @constructor
  * @param service              Web service to use
  * @param accountId            Account, also known as wallet ID in OKPay terms
  * @param tokenGenerator       Generator of valid request tokens
  */
class OkPayWebServiceClient(
    service: OkPayWebService.Service,
    override val accountId: String,
    tokenGenerator: TokenGenerator) extends OkPayClient {

  import OkPayWebServiceClient._

  /** Alternative web service client constructor
    *
    * @param service              Web service to use
    * @param accountId            Account, also known as wallet ID in OKPay terms
    * @param seedToken            Token used to generate request tokens
    */
  def this(service: OkPayWebService.Service, accountId: String, seedToken: String) =
    this(service, accountId, new TokenGenerator(seedToken))

  override implicit protected val executionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  override def sendPayment[C <: FiatCurrency](
      to: AccountId, amount: CurrencyAmount[C], comment: String, feePolicy: FeePolicy): Future[Payment[C]] =
    service.send_Money(
      walletID = Some(Some(accountId)),
      securityToken = Some(Some(buildCurrentToken())),
      receiver = Some(Some(to)),
      currency = Some(Some(amount.currency.javaCurrency.getCurrencyCode)),
      amount = Some(amount.value),
      comment = Some(Some(comment)),
      isReceiverPaysFees = Some(feePolicy match {
        case PaidByReceiver => true
        case PaidBySender => false
      }),
      invoice = None
    ).map { response =>
      parsePaymentOfCurrency(response.Send_MoneyResult.flatten.get, amount.currency)
    }

  override def findPayment(paymentId: PaymentId): Future[Option[AnyPayment]] =
    service.transaction_Get(
      walletID = Some(Some(accountId)),
      securityToken = Some(Some(buildCurrentToken())),
      txnID = Some(paymentId.toLong),
      invoice = None
    ).map { result =>
      result.Transaction_GetResult.flatten.map(parsePayment)
    }.recover {
      case Soap11Fault(Fault(_, TransactionNotFoundFault, _, _), _, _) => None
    }

  override def currentBalances(): Future[Seq[FiatAmount]] = {
    service.wallet_Get_Balance(
      walletID = Some(Some(accountId)),
      securityToken = Some(Some(buildCurrentToken()))
    ).map { response =>
      (for {
        arrayOfBalances <- response.Wallet_Get_BalanceResult.flatten
      } yield parseBalances(arrayOfBalances)).getOrElse(
          throw new PaymentProcessorException(s"Cannot parse balances in $response"))
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
          statusOpt) =>
        val amount = FiatAmount(net, txInfo.Currency.get.get)
        val date = DateFormat.parseDateTime(rawDate)
        val isCompleted = statusOpt.getOrElse(NoneType) == Completed
        Payment(paymentId.toString, senderId, receiverId, amount, date, description, isCompleted)

      case _ => throw new PaymentProcessorException(s"Cannot parse the sent payment: $txInfo")
    }
  }

  private def parseBalances[C <: FiatCurrency](balances: ArrayOfBalance): Seq[FiatAmount] = {
    balances.Balance.collect {
      case Some(Balance(Some(amount), Flatten(currencyCode))) => FiatAmount(amount, currencyCode)
    }
  }

  private def buildCurrentToken() = tokenGenerator.build(DateTime.now(DateTimeZone.UTC))
}

object OkPayWebServiceClient {

  val DateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  private val TransactionNotFoundFault = "Transaction_Not_Found"

  private object WalletId {
    def unapply(info: AccountInfo): Option[String] = info.WalletID.flatten
  }

  private object Flatten {
    def unapply[T](option: Option[Option[T]]): Option[T] = option.flatten
  }
}
