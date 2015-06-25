package coinffeine.peer.exchange.micropayment

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.exchange.ActiveExchange
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate

trait ProgressExpectations { this: AkkaSpec =>

  protected def listener: TestProbe
  protected def exchange: ActiveExchange

  def expectProgress(signatures: Int): Unit = {
    val progress = listener.expectMsgType[ExchangeUpdate].exchange.progress
    withClue(s"Expecting $signatures signatures: ") {
      val actualSignatures = stepOf(progress.bitcoinsTransferred,
        exchange.amounts.intermediateSteps.map(_.progress.bitcoinsTransferred))
      actualSignatures shouldBe signatures
    }
  }

  private def stepOf[A](value: A, steps: Seq[A]): Int = steps.indexOf(value) + 1
}
