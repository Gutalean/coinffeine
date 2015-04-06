package coinffeine.peer.bitcoin.wallet

import scala.util.Random
import scalaz.Scalaz

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.Hash
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.bitcoin.wallet.BlockedOutputs.Output
import coinffeine.peer.bitcoin.wallet.WalletActor.{NotEnoughFunds, UnknownFunds}

class BlockedOutputsTest extends UnitTest {
  import Scalaz._

  private def instanceWithOutputs(numberOfOneBtcOutputs: Int): BlockedOutputs = {
    val instance = new BlockedOutputs()
    instance.setSpendCandidates(buildOutputs(numberOfOneBtcOutputs))
    instance
  }

  private def buildOutputs(numberOfOneBtcOutputs: Int): Set[Output] = {
    Seq.fill(numberOfOneBtcOutputs)(Output(
      txHash = randomHash(),
      index = Random.nextInt(10),
      value = 1.BTC
    )).toSet
  }

  private def randomHash() = {
    val bytes = new Array[Byte](32)
    Random.nextBytes(bytes)
    new Hash(bytes)
  }

  val fundsId = ExchangeId("1")

  "A blocked outputs object" should "report spendable funds" in {
    val instance = instanceWithOutputs(5)
    instance.spendable shouldBe 5.BTC
    instance.block(fundsId, instance.collectFunds(2.BTC).get)
    instance.spendable shouldBe 5.BTC
  }

  it should "report available funds" in {
    val instance = instanceWithOutputs(5)
    instance.available shouldBe 5.BTC
    instance.block(fundsId, instance.collectFunds(2.BTC).get)
    instance.available shouldBe 3.BTC
  }

  it should "report blocked funds" in {
    val instance = instanceWithOutputs(5)
    instance.blocked shouldBe 0.BTC
    val outputs = instance.collectFunds(2.BTC).get
    instance.block(fundsId, outputs)
    instance.blocked shouldBe 2.BTC
    instance.use(fundsId, outputs.take(1))
    instance.blocked shouldBe 2.BTC
  }

  it should "collect funds until exhausting spendable outputs" in {
    val instance = instanceWithOutputs(5)
    instance.collectFunds(2.5.BTC) shouldBe 'nonEmpty
    instance.collectFunds(5.BTC) shouldBe 'nonEmpty
    instance.collectFunds(5.1.BTC) shouldBe 'empty
  }

  it should "unblock previously blocked funds to make them available again" in {
    val instance = instanceWithOutputs(2)
    instance.block(fundsId, instance.collectFunds(2.BTC).get)
    instance.unblock(fundsId)
    instance.collectFunds(2.BTC) shouldBe 'nonEmpty
  }

  it should "allow collecting funds as new outputs are spendable" in {
    val instance = new BlockedOutputs()
    val initialOutputs = buildOutputs(1)
    instance.setSpendCandidates(initialOutputs)
    instance.collectFunds(3.BTC) shouldBe 'empty
    instance.setSpendCandidates(initialOutputs ++ buildOutputs(2))
    instance.collectFunds(3.BTC) shouldBe 'nonEmpty
  }

  it should "reject blocking funds for the same id twice" in {
    val instance = new BlockedOutputs()
    instance.setSpendCandidates(buildOutputs(2))
    instance.block(fundsId, instance.collectFunds(1.BTC).get)
    val funds = instance.collectFunds(1.BTC).get
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.block(fundsId, funds)
    }
  }

  it should "reject using funds from an unknown identifier" in {
    val instance = new BlockedOutputs()
    instance.canUse(ExchangeId("unknown"), 2.BTC) shouldBe UnknownFunds.failure
  }

  it should "reject using more funds that previously blocked" in {
    val instance = instanceWithOutputs(2)
    instance.block(fundsId, instance.collectFunds(2.BTC).get)
    instance.canUse(fundsId, 1000.BTC) shouldBe NotEnoughFunds(1000.BTC, 2.BTC).failure
  }

  it should "use and cancel usage of outputs" in {
    val instance = instanceWithOutputs(2)
    instance.block(fundsId, instance.collectFunds(2.BTC).get)
    val funds = instance.canUse(fundsId, 2.BTC).getOrElse(fail())
    instance.use(fundsId, funds)
    instance.cancelUsage(funds)
    instance.canUse(fundsId, 2.BTC) shouldBe 'success
  }
}
