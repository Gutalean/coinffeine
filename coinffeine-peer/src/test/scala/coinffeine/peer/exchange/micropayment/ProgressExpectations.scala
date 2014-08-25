package coinffeine.peer.exchange.micropayment

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency._
import coinffeine.model.exchange.AnyExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress

trait ProgressExpectations { this: AkkaSpec =>

  protected def listener: TestProbe
  protected def exchange: AnyExchange[_ <: FiatCurrency]

  def expectProgress(signatures: Int, payments: Int): Unit = {
    val progress = listener.expectMsgClass(classOf[ExchangeProgress]).exchange.progress
    val actualSignatures =
      countUntilAddingTo(progress.bitcoinsTransferred, exchange.amounts.steps.map(_.bitcoinAmount))
    val actualPayments =
      countUntilAddingTo(progress.fiatTransferred, exchange.amounts.steps.map(_.fiatAmount))
    withClue("Wrong number of signatures") {
      actualSignatures should be (signatures)
    }
    withClue("Wrong number of payments") {
      actualPayments should be(payments)
    }
  }

  def countUntilAddingTo[C <: Currency](total: CurrencyAmount[C],
                                        elements: Seq[CurrencyAmount[C]]): Int =
    elements.scan(total.currency.Zero)(_ + _)
      .takeWhile(_ <= total)
      .size - 1
}
