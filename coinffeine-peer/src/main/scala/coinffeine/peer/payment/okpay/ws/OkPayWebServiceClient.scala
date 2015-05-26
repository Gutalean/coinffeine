package coinffeine.peer.payment.okpay.ws

import scala.concurrent.Future
import scalaxb.Soap11Fault

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import soapenvelope11.Fault

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor.{AccountId, PaymentId}
import coinffeine.model.payment.{AnyPayment, Payment}
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.OkPayClient._
import coinffeine.peer.payment.okpay.generated._
import coinffeine.peer.payment.okpay.{OkPayClient, TokenGenerator}

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

  import coinffeine.peer.payment.okpay.ws.OkPayWebServiceClient._

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
    }.mapSoapFault {
      case UnsupportedPaymentMethodFault => UnsupportedPaymentMethod
      case ReceiverNotFoundFault => ReceiverNotFound(to, _)
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
    }.mapSoapFault()

  override def currentBalances(): Future[Seq[FiatAmount]] =
    service.wallet_Get_Balance(
      walletID = Some(Some(accountId)),
      securityToken = Some(Some(buildCurrentToken()))
    ).map { response =>
      (for {
        arrayOfBalances <- response.Wallet_Get_BalanceResult.flatten
      } yield parseBalances(arrayOfBalances)).getOrElse(
          throw new PaymentProcessorException(s"Cannot parse balances in $response"))
    }.mapSoapFault()

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

  implicit class PimpMyFuture[A](future: Future[A]) {

    type SoapFaultMapper = PartialFunction[String, Throwable => OkPayClient.Error]

    def mapSoapFault(specificFaultMapper: SoapFaultMapper = Map.empty): Future[A] = {
      val generalFaultMapper: SoapFaultMapper = {
        case AccountNotFoundFault => AccountNotFound(accountId, _)
        case AuthenticationFailedFault => AuthenticationFailed(accountId, _)
        case CurrencyDisabledFault => CurrencyDisabled
        case NotEnoughMoneyFault => NotEnoughMoney(accountId, _)
        case InternalErrorFault => InternalError
        case e => UnexpectedError
      }
      future.recoverWith {
        case e @ Soap11Fault(Fault(_, fault, _, _), _, _) =>
          Future.failed(specificFaultMapper.orElse(generalFaultMapper)(fault)(e))
      }
    }
  }
}

object OkPayWebServiceClient {

  val DateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC()

  private val AccountNotFoundFault = "Account_Not_Found"
  private val AuthenticationFailedFault = "Authentication_Failed"
  private val CurrencyDisabledFault = "Currency_Disabled"
  private val NotEnoughMoneyFault = "Not_Enough_Money"
  private val TransactionNotFoundFault = "Transaction_Not_Found"
  private val UnsupportedPaymentMethodFault = "Payment_Method_Not_Supported"
  private val ReceiverNotFoundFault = "Receiver_Not_Found"
  private val InternalErrorFault = "Internal_Error"

  private object WalletId {
    def unapply(info: AccountInfo): Option[String] = info.WalletID.flatten
  }

  private object Flatten {
    def unapply[T](option: Option[Option[T]]): Option[T] = option.flatten
  }
}
