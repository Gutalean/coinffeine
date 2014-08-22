package coinffeine.peer.exchange.micropayment

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.exchange.AnyExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress

trait ProgressExpectations { this: AkkaSpec =>

  protected def listener: TestProbe
  protected def exchange: AnyExchange[_]

  def expectProgress(signatures: Int, payments: Int): Unit = {
    val progress = listener.expectMsgClass(classOf[ExchangeProgress]).exchange.progress
    val actualSignatures =
      progress.bitcoinsTransferred.value / exchange.amounts.stepAmounts.bitcoinAmount.value
    val actualPayments = progress.fiatTransferred.value / exchange.amounts.stepAmounts.fiatAmount.value
    withClue("Wrong number of signatures") {
      actualSignatures should be (signatures)
    }
    withClue("Wrong number of payments") {
      actualPayments should be(payments)
    }
  }
}
