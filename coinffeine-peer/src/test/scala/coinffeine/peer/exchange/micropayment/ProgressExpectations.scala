package coinffeine.peer.exchange.micropayment

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency._
import coinffeine.model.exchange.AnyStateExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress

trait ProgressExpectations[C <: FiatCurrency] { this: AkkaSpec =>

  protected def listener: TestProbe
  protected def exchange: AnyStateExchange[C]

  def expectProgress(signatures: Int, payments: Int): Unit = {
    val progress = listener.expectMsgType[ExchangeProgress].exchange.progress
    progress.fiatTransferred.currency should be (exchange.currency)
    val fiat = progress.fiatTransferred.asInstanceOf[CurrencyAmount[C]]
    withClue("Wrong number of payments") {
      val actualPayments =
        countUntilAddingTo(fiat, exchange.amounts.intermediateSteps.map(_.fiatAmount))
      actualPayments shouldBe payments
    }
    withClue("Wrong number of signatures") {
      val actualSignatures = exchange.amounts.intermediateSteps.map(_.depositSplit.buyer)
        .indexOf(progress.bitcoinsTransferred) + 1
      actualSignatures shouldBe signatures
    }
  }

  def countUntilAddingTo(total: CurrencyAmount[C], elements: Seq[CurrencyAmount[C]]): Int =
    elements.scan(CurrencyAmount.zero(total.currency))(_ + _)
      .takeWhile(_ <= total)
      .size - 1
}
