package coinffeine.peer.exchange.micropayment

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency._
import coinffeine.model.exchange.AnyStateExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate

trait ProgressExpectations[C <: FiatCurrency] { this: AkkaSpec =>

  protected def listener: TestProbe
  protected def exchange: AnyStateExchange[C]

  def expectProgress(signatures: Int, payments: Int): Unit = {
    val progress = listener.expectMsgType[ExchangeUpdate].exchange.progress
    progress.fiatTransferred.currency should be (exchange.currency)
    val fiat = progress.fiatTransferred.asInstanceOf[CurrencyAmount[C]]

    withClue("Wrong number of payments") {
      val actualPayments = stepOf(fiat,
        exchange.amounts.intermediateSteps.map(_.progress.fiatTransferred))
      actualPayments shouldBe payments
    }
    withClue("Wrong number of signatures") {
      val actualSignatures = stepOf(progress.bitcoinsTransferred,
        exchange.amounts.intermediateSteps.map(_.progress.bitcoinsTransferred))
      actualSignatures shouldBe signatures
    }
  }

  private def stepOf[A](value: A, steps: Seq[A]): Int = steps.indexOf(value) + 1
}
