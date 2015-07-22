package coinffeine.peer.payment.okpay.ws

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scalaxb.Soap11Fault

import com.typesafe.scalalogging.StrictLogging
import org.joda.time.{DateTime, DateTimeZone, Interval}
import soapenvelope11.Fault

import coinffeine.common.time.Month
import coinffeine.model.currency._
import coinffeine.model.payment.Payment
import coinffeine.model.payment.PaymentProcessor.{AccountId, Invoice, PaymentId}
import coinffeine.model.payment.okpay._
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.OkPayClient._
import coinffeine.peer.payment.okpay.generated._
import coinffeine.peer.payment.okpay.{FeePolicy, OkPayClient, TokenGenerator}

/** SOAP client of OKPay service.
  *
  * @constructor
  * @param service              Web service to use
  * @param cachedToken          Cache of security tokens
  * @param accountId            Account, also known as wallet ID in OKPay terms
  * @param tokenGenerator       Generator of valid request tokens
  * @param periodicLimits       Periodic limits
  */
class OkPayWebServiceClient(
    service: OkPayWebService.Service,
    cachedToken: AtomicReference[Option[String]],
    override val accountId: String,
    tokenGenerator: TokenGenerator,
    periodicLimits: FiatAmounts) extends OkPayClient with StrictLogging {

  import OkPayWebServiceClient._

  /** Alternative web service client constructor
    *
    * @param service              Web service to use
    * @param cachedToken          Cache of security tokens
    * @param accountId            Account, also known as wallet ID in OKPay terms
    * @param seedToken            Token used to generate request tokens
    * @param periodicLimits       Periodic limits
    */
  def this(
      service: OkPayWebService.Service,
      cachedToken: AtomicReference[Option[String]],
      accountId: String,
      seedToken: String,
      periodicLimits: FiatAmounts) =
    this(service, cachedToken, accountId, new TokenGenerator(seedToken), periodicLimits)

  override implicit protected val executionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  override def sendPayment(
      to: AccountId,
      amount: FiatAmount,
      comment: String,
      invoice: Invoice,
      feePolicy: FeePolicy): Future[Payment] =
    authenticatedRequest { token =>
      service.send_Money(
        walletID = SSome(accountId),
        securityToken = SSome(token),
        receiver = SSome(to),
        currency = SSome(amount.currency.javaCurrency.getCurrencyCode),
        amount = Some(amount.value),
        comment = SSome(comment),
        isReceiverPaysFees = Some(feePolicy match {
          case FeePolicy.PaidByReceiver => true
          case FeePolicy.PaidBySender => false
        }),
        invoice = SSome(invoice)
      ).map { response =>
        parsePaymentOfCurrency(response.Send_MoneyResult.flatten.get, amount.currency)
      }.mapSoapFault {
        case OkPayFault(OkPayFault.UnsupportedPaymentMethod) => UnsupportedPaymentMethod
        case OkPayFault(OkPayFault.ReceiverNotFound) => ReceiverNotFound(to, _)
        case OkPayFault(OkPayFault.DuplicatePayment) => DuplicatedPayment(to, invoice, _)
      }
    }

  override def findPaymentById(paymentId: PaymentId): Future[Option[Payment]] =
    findPayment(Left(paymentId))

  override def findPaymentByInvoice(invoice: Invoice): Future[Option[Payment]] =
    findPayment(Right(invoice))

  private def findPayment(params: Either[PaymentId, Invoice]) =
    authenticatedRequest { token =>
      service.transaction_Get(
        walletID = SSome(accountId),
        securityToken = SSome(token),
        txnID = params.left.toOption.map(_.toLong),
        invoice = params.right.toOption.map(Some(_))
      ).map { result =>
        result.Transaction_GetResult.flatten.map(parseTransaction)
      }.recover {
        case Soap11Fault(Fault(_, OkPayFault(OkPayFault.TransactionNotFound), _, _), _, _) => None
      }.mapSoapFault()
    }

  override def checkExistence(id: AccountId): Future[Boolean] =
    authenticatedRequest { token =>
      service.account_Check(
        walletID =  SSome(accountId),
        securityToken = SSome(token),
        account = SSome(id)
      ).map(parseAccountCheckResponse).mapSoapFault()
    }

  private def parseAccountCheckResponse(response: Account_CheckResponse): Boolean =
    response.Account_CheckResult match {
      case Some(walletOwner) if walletOwner != UnknownWalletOwner => true
      case _ => false
    }

  override def currentBalances(): Future[FiatAmounts] =
    authenticatedRequest { token =>
      service.wallet_Get_Balance(
        walletID = SSome(accountId),
        securityToken = SSome(token)
      ).map { response =>
        (for {
          arrayOfBalances <- response.Wallet_Get_BalanceResult.flatten
        } yield parseBalances(arrayOfBalances)).getOrElse(
            throw new PaymentProcessorException(s"Cannot parse balances in $response"))
      }.mapSoapFault()
    }

  override def currentRemainingLimits(): Future[FiatAmounts] =
    if (periodicLimits.amounts.count(_.isPositive) == 0) Future.successful(periodicLimits)
    else currentPeriodUsage().map(remainingLimits)

  private def remainingLimits(usage: FiatAmounts): FiatAmounts =
    FiatAmounts(periodicLimits.amounts.map { limit =>
      remainingLimit(limit, usage)
    })

  private def remainingLimit(limit: FiatAmount, usage: FiatAmounts): FiatAmount =
    decreaseAmount(limit, usageFor(usage, limit.currency))

  private def decreaseAmount(amount: FiatAmount, delta: FiatAmount): FiatAmount =
    (amount - delta) max amount.currency.zero

  private def usageFor(usage: FiatAmounts, currency: FiatCurrency): FiatAmount =
    usage.get(currency).getOrElse(currency.zero)

  private def currentPeriodUsage(): Future[FiatAmounts] = {
    val currentInterval = Month.containing(DateTime.now(DateTimeZone.UTC))
    val firstPage = Page.first(Page.MaxSize)
    val allPages = Stream.iterate(firstPage)(_.next).map(page => transactionHistoryPage(currentInterval, page))
    for {
      firstResult <- allPages.head
      nonEmptyPages = allPages.take(firstResult.totalPages).toVector
      amounts <- Future.fold(nonEmptyPages)(FiatAmounts.empty)(_ + usage(_))
    } yield amounts
  }

  private def usage(result: TransactionHistoryPage): FiatAmounts = {
    result.transactions.foldLeft(FiatAmounts.empty)(_ + usage(_))
  }

  private def usage(transaction: Transaction): FiatAmounts = {
    if (transaction.senderId == accountId && transaction.status.affectsLimits) {
      FiatAmounts.fromAmounts(transaction.grossAmount)
    } else FiatAmounts.empty
  }

  private def transactionHistoryPage(
      interval: Interval, page: Page): Future[TransactionHistoryPage] =
    authenticatedRequest { token =>
      logger.debug("Fetching transaction history page {} for interval {}",
        page.number.toString, interval)
      service.transaction_History(
        walletID = SSome(accountId),
        securityToken = SSome(token),
        from = SSome(OkPayDate(interval.getStart)),
        till = SSome(OkPayDate(interval.getEnd)),
        pageSize = Some(page.size),
        pageNumber = Some(page.number)
      ).map(parseTransactionHistoryResponse).mapSoapFault()
    }

  private def parseTransactionHistoryResponse(
      response: Transaction_HistoryResponse): TransactionHistoryPage = {
    response.Transaction_HistoryResult match {
      case SSome(HistoryInfo(
          Some(_pageCount),
          Some(pageNumber),
          Some(pageSize),
          Some(totalSize),
          SSome(arrayOfTransactions))) =>
        val page = Page(size = pageSize, number = pageNumber)
        val transactions = arrayOfTransactions.TransactionInfo.flatten.map(parseTransaction)
        TransactionHistoryPage(page, totalSize, transactions)

      case _ =>
        throw new PaymentProcessorException(s"Cannot parse the transaction history: $response")
    }
  }

  private def parsePaymentOfCurrency(
      txInfo: TransactionInfo, expectedCurrency: FiatCurrency): Transaction = {
    val payment = parseTransaction(txInfo)
    val actualCurrency = payment.netAmount.currency
    if (actualCurrency != expectedCurrency) {
      throw new PaymentProcessorException(
        s"payment is expressed in $actualCurrency, but $expectedCurrency was expected")
    }
    payment
  }

  private def parseTransaction(txInfo: TransactionInfo): Transaction = txInfo match {
    case TransactionInfo(
        Some(amount),
        Flatten(description),
        Flatten(rawCurrency),
        Flatten(OkPayDate(date)),
        maybeFee,
        Some(paymentId),
        Flatten(invoice),
        Some(net),
        _,
        Flatten(WalletId(receiverId)),
        Flatten(WalletId(senderId)),
        maybeStatus) =>
      val currency = FiatCurrency(txInfo.Currency.get.get)
      val amount = currency(net)
      val fee = maybeFee.fold(currency.zero)(currency.apply)
      Transaction(paymentId, senderId, receiverId, amount, fee, date, description,
        invoice, parseStatus(maybeStatus))

    case _ => throw new PaymentProcessorException(s"Cannot parse the sent payment: $txInfo")
  }

  private def parseStatus(maybeStatus: Option[OperationStatus]): TransactionStatus =
    maybeStatus.getOrElse(NoneType) match {
      case NoneType => TransactionStatus.None
      case Error => TransactionStatus.Error
      case Canceled => TransactionStatus.Canceled
      case Pending => TransactionStatus.Pending
      case Reversed => TransactionStatus.Reversed
      case Hold => TransactionStatus.Hold
      case Completed => TransactionStatus.Completed
    }

  private def parseBalances(balances: ArrayOfBalance): FiatAmounts = FiatAmounts(
    balances.Balance.collect {
      case Some(Balance(Some(amount), Flatten(currencyCode))) =>
        FiatCurrency(currencyCode)(amount)
    }
  )

  /** Wraps a request that needs an authentication token and makes sure that it is
    * retried in case of authentication fails once.
    */
  private def authenticatedRequest[A](block: String => Future[A]): Future[A] = {
    val attempt = currentToken().flatMap(block)
    attempt.recoverWith { case AuthenticationFailed(_, _) => attempt }
  }

  private def currentToken(): Future[String] =
    cachedToken.get().map(Future.successful)
      .getOrElse(createFreshToken())

  private def createFreshToken(): Future[String] = for {
    time <- lookupServerTime()
  } yield {
    val token = tokenGenerator.build(time)
    cachedToken.set(Some(token))
    logger.info("Generated new security token with server time {}", time)
    token
  }

  private def lookupServerTime(): Future[DateTime] =
    service.get_Date_Time().map { response =>
      response.Get_Date_TimeResult.flatten
        .fold(throw new PaymentProcessorException("Empty getDateTime response"))(OkPayDate.Format.parseDateTime)
    }.mapSoapFault()

  implicit class PimpMyFuture[A](future: Future[A]) {

    type SoapFaultMapper = PartialFunction[String, Throwable => OkPayClient.Error]

    def mapSoapFault(specificFaultMapper: SoapFaultMapper = Map.empty): Future[A] = {
      val generalFaultMapper: SoapFaultMapper = {
        case OkPayFault(OkPayFault.ClientNotFound) => ClientNotFound(accountId, _)
        case OkPayFault(OkPayFault.AuthenticationFailed) => (cause: Throwable) => {
          expireToken()
          AuthenticationFailed(accountId, cause)
        }
        case OkPayFault(OkPayFault.NotEnoughMoney) => NotEnoughMoney(accountId, _)
        case OkPayFault(OkPayFault.DisabledCurrency) => DisabledCurrency
        case OkPayFault(OkPayFault.InternalError) => InternalError
        case OkPayFault(unexpectedFault) => UnexpectedError(unexpectedFault, _)
        case unknownFault => UnsupportedError(unknownFault, _)
      }
      future.recoverWith {
        case e @ Soap11Fault(Fault(_, fault, _, _), _, _) =>
          Future.failed(specificFaultMapper.orElse(generalFaultMapper)(fault)(e))
      }
    }
  }

  private def expireToken(): Unit = {
    logger.info("Security token expired")
    cachedToken.set(None)
  }
}

object OkPayWebServiceClient {

  private val UnknownWalletOwner = 0L

  private object WalletId {
    def unapply(info: AccountInfo): Option[String] = info.WalletID.flatten
  }

  private object Flatten {
    def unapply[T](option: Option[Option[T]]): Option[T] = option.flatten
  }
}
