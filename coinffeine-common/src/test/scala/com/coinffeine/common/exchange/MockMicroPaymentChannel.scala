package com.coinffeine.common.exchange

import scala.util.{Failure, Success}

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.RunningExchange
import com.coinffeine.common.exchange.MicroPaymentChannel._

class MockMicroPaymentChannel[C <: FiatCurrency] private (
     override val exchange: RunningExchange[C],  step: Step) extends MicroPaymentChannel[C] {

  def this(exchange: RunningExchange[C]) =
    this(exchange, IntermediateStep(1, exchange.amounts.breakdown))

  override val currentStep = step

  override def nextStep = new MockMicroPaymentChannel(exchange, step.next)

  override def validateCurrentTransactionSignatures(signatures: Signatures) =
    signatures match {
      case Signatures(MockExchangeProtocol.InvalidSignature, _) |
           Signatures(_, MockExchangeProtocol.InvalidSignature) =>
        Failure(new Error("Invalid signature"))
      case _ => Success {}
    }

  override def closingTransaction(counterpartSignatures: Signatures) =
    buildDummyTransaction(step.value - 1)

  private def buildDummyTransaction(idx: Int) = {
    val tx = new MutableTransaction(exchange.parameters.network)
    tx.setLockTime(idx.toLong) // Ensures that generated transactions do not have the same hash
    ImmutableTransaction(tx)
  }

  override def signCurrentTransaction = MockExchangeProtocol.DummySignatures
}
