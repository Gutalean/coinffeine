package coinffeine.peer.exchange.protocol

import java.math.BigInteger
import scala.util.{Failure, Success, Try}

import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Both, Exchange, HandshakingExchange, RunningExchange}

class MockExchangeProtocol extends ExchangeProtocol {

  override def createHandshake[C <: FiatCurrency](
      exchange: HandshakingExchange[C],
      deposit: ImmutableTransaction) = new MockHandshake(exchange)

  override def createMicroPaymentChannel[C <: FiatCurrency](exchange: RunningExchange[C]) =
    new MockMicroPaymentChannel(exchange)

  override def validateDeposits(transactions: Both[ImmutableTransaction],
                                exchange: HandshakingExchange[FiatCurrency]): Try[Exchange.Deposits] =
    validateCommitments(transactions, null).map(_ => Exchange.Deposits(transactions))

  override def validateCommitments(transactions: Both[ImmutableTransaction],
                                   amounts: Exchange.Amounts[FiatCurrency]): Try[Unit] =
    transactions.toSeq match {
      case Seq(MockExchangeProtocol.InvalidDeposit, _) =>
        Failure(new IllegalArgumentException("Invalid buyer deposit"))
      case Seq(_, MockExchangeProtocol.InvalidDeposit) =>
        Failure(new IllegalArgumentException("Invalid seller deposit"))
      case _ => Success {}
    }
}

object MockExchangeProtocol {

  val DummyDeposit = ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))
  val DummyDeposits = Exchange.Deposits(Both(DummyDeposit, DummyDeposit))

  /** Magic deposit that is always rejected */
  val InvalidDeposit = ImmutableTransaction {
    val tx = new MutableTransaction(CoinffeineUnitTestNetwork)
    tx.setLockTime(42)
    tx
  }

  val DummySignatures = Both(TransactionSignature.dummy, TransactionSignature.dummy)

  /** Magic signature that is always rejected */
  val InvalidSignature = new TransactionSignature(BigInteger.valueOf(42), BigInteger.valueOf(42))

  val RefundSignature = new TransactionSignature(BigInteger.ZERO, BigInteger.ZERO)
  val CounterpartRefundSignature = new TransactionSignature(BigInteger.ONE, BigInteger.ONE)
}
