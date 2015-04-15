package coinffeine.peer.exchange.protocol

import scala.util.{Failure, Success}

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.RunningExchange
import coinffeine.peer.exchange.protocol.MicroPaymentChannel._

class MockMicroPaymentChannel[C <: FiatCurrency] private (
     override val exchange: RunningExchange[C],  step: Step) extends MicroPaymentChannel[C] {

  def this(exchange: RunningExchange[C]) =
    this(exchange, IntermediateStep(1, exchange.amounts.breakdown))

  override val currentStep = step

  override def nextStep = new MockMicroPaymentChannel(exchange, step.next)

  override def validateCurrentTransactionSignatures(signatures: Both[TransactionSignature]) =
    signatures match {
      case Both(MockExchangeProtocol.InvalidSignature, _) |
           Both(_, MockExchangeProtocol.InvalidSignature) =>
        Failure(new Error("Invalid signature"))
      case _ => Success {}
    }

  override def closingTransaction(counterpartSignatures: Both[TransactionSignature]) =
    buildDummyTransaction(step.value - 1)

  private def buildDummyTransaction(idx: Int) = {
    val tx = new MutableTransaction(exchange.parameters.network)
    tx.setLockTime(idx.toLong) // Ensures that generated transactions do not have the same hash
    ImmutableTransaction(tx)
  }

  override def signCurrentTransaction = MockExchangeProtocol.DummySignatures
}
