package coinffeine.peer.exchange.protocol

import java.math.BigInteger
import scalaz.syntax.validation._

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._

class MockExchangeProtocol extends ExchangeProtocol {

  override def createHandshake[C <: FiatCurrency](
      exchange: DepositPendingExchange[C],
      deposit: ImmutableTransaction) = new MockHandshake(exchange)

  override def createMicroPaymentChannel[C <: FiatCurrency](exchange: RunningExchange[C]) =
    new MockMicroPaymentChannel(exchange)

  override def validateDeposits(transactions: Both[ImmutableTransaction],
                                amounts: Exchange.Amounts[_ <: FiatCurrency],
                                requiredSignatures: Both[PublicKey],
                                network: Network): Both[DepositValidation] =
    transactions.map {
      case MockExchangeProtocol.InvalidDeposit => NoMultiSig.failureNel
      case _ => ().successNel
    }
}

object MockExchangeProtocol {

  val DummyDeposit = ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))
  val DummyDeposits = Both(DummyDeposit, DummyDeposit)

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
