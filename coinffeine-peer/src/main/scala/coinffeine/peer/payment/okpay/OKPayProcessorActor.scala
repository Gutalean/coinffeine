package coinffeine.peer.payment.okpay

import java.util.{Currency => JavaCurrency}
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorRef, Props}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.Payment
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.generated._

class OKPayProcessorActor(
    account: String,
    service: I_OkPayAPI,
    tokenGenerator: TokenGenerator) extends Actor {

  override def receive: Receive = {
    case PaymentProcessor.Identify =>
      sender ! PaymentProcessor.Identified(OKPayProcessorActor.Id)
    case pay: PaymentProcessor.Pay[_] =>
      sendPayment(sender(), pay)
    case PaymentProcessor.FindPayment(paymentId) =>
      findPayment(sender(), paymentId)
    case PaymentProcessor.RetrieveBalance(currency) =>
      currentBalance(sender(), currency)
  }

  private def sendPayment[C <: FiatCurrency](requester: ActorRef,
                                             pay: PaymentProcessor.Pay[C]): Unit = {
    (for {
      response <- service.send_Money(
        walletID = Some(Some(account)),
        securityToken = Some(Some(buildCurrentToken())),
        receiver = Some(Some(pay.to)),
        currency = Some(Some(pay.amount.currency.javaCurrency.getCurrencyCode)),
        amount = Some(pay.amount.value),
        comment = Some(Some(pay.comment)),
        isReceiverPaysFees = Some(false),
        invoice = None
      )
    } yield parsePaymentOfCurrency(response.Send_MoneyResult.flatten.get, pay.amount.currency)
    ).onComplete {
      case Success(payment) =>
        requester ! PaymentProcessor.Paid(payment)
      case Failure(error) =>
        requester ! PaymentProcessor.PaymentFailed(pay, error)
    }
  }

  private def findPayment(requester: ActorRef, paymentId: String): Unit = {
    service.transaction_Get(
      walletID = Some(Some(this.account)),
      securityToken = Some(Some(buildCurrentToken())),
      txnID = Some(paymentId.toLong),
      invoice = None
    ).map { result =>
      result.Transaction_GetResult.flatten.map(parsePayment)
    }
  }.onComplete {
    case Success(Some(payment)) => requester ! PaymentProcessor.PaymentFound(payment)
    case Success(None) => requester ! PaymentProcessor.PaymentNotFound(paymentId)
    case Failure(error) => requester ! PaymentProcessor.FindPaymentFailed(paymentId, error)
  }

  private def currentBalance[C <: FiatCurrency](requester: ActorRef,
                                                currency: C): Unit = {
    service.wallet_Get_Balance(
      walletID = Some(Some(this.account)),
      securityToken = Some(Some(buildCurrentToken()))
    ).map { response =>
      response.Wallet_Get_BalanceResult.flatten.map(b => parseArrayOfBalance(b, currency))
        .getOrElse(throw new PaymentProcessorException("Cannot parse balances: " + response))
    }.onComplete {
      case Success(balance) => requester ! PaymentProcessor.BalanceRetrieved(balance)
      case Failure(error) => requester ! PaymentProcessor.BalanceRetrievalFailed(currency, error)
    }
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
        _
      ) =>
        val currency = FiatCurrency(JavaCurrency.getInstance(txInfo.Currency.get.get))
        val amount = currency.amount(net)
        val date = DateTimeFormat.forPattern(OKPayProcessorActor.DateFormat).parseDateTime(rawDate)
        Payment(paymentId.toString, senderId, receiverId, amount, date, description)
      case _ => throw new PaymentProcessorException(s"Cannot parse the sent payment: $txInfo")
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

  private object WalletId {
    def unapply(info: AccountInfo): Option[String] = info.WalletID.flatten
  }

  private object Flatten {
    def unapply[T](option: Option[Option[T]]): Option[T] = option.flatten
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

object OKPayProcessorActor {

  val Id = "OKPAY"

  trait Component extends PaymentProcessor.Component { this: ConfigComponent =>
    override lazy val paymentProcessorProps = Props {
      val accountId = config.getString("coinffeine.okpay.id")
      val token = config.getString("coinffeine.okpay.token")
      new OKPayProcessorActor(accountId, new OKPayClient().service, new TokenGenerator(token))
    }
  }

  private val DateFormat = "yyyy-MM-dd HH:mm:ss"
}
