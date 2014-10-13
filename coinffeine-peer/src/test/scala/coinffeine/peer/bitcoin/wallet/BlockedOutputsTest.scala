package coinffeine.peer.bitcoin.wallet

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.bitcoin.wallet.BlockedOutputs.{FundsUseException, UnknownFunds}

class BlockedOutputsTest extends UnitTest with CoinffeineUnitTestNetwork.Component {

  private def instanceWithOutputs(numberOfOneBtcOutputs: Int): BlockedOutputs = {
    val instance = new BlockedOutputs()
    instance.setSpendCandidates(buildOutputs(numberOfOneBtcOutputs))
    instance
  }

  private def buildOutputs(numberOfOneBtcOutputs: Int): Set[MutableTransactionOutput] = {
    val tx = new MutableTransaction(network)
    Seq.fill(numberOfOneBtcOutputs) {
      new MutableTransactionOutput(network, tx, 1.BTC, new PublicKey())
    }.toSet
  }

  "A blocked outputs object" should "report spendable funds" in {
    val instance = instanceWithOutputs(5)
    instance.spendable shouldBe 5.BTC
    instance.block(ExchangeId("1"), 2.BTC)
    instance.spendable shouldBe 5.BTC
  }

  it should "report available funds" in {
    val instance = instanceWithOutputs(5)
    instance.available shouldBe 5.BTC
    instance.block(ExchangeId("1"), 2.BTC)
    instance.available shouldBe 3.BTC
  }

  it should "report blocked funds" in {
    val instance = instanceWithOutputs(5)
    instance.blocked shouldBe 0.BTC
    instance.block(ExchangeId("1"), 2.BTC)
    instance.blocked shouldBe 2.BTC
  }

  it should "block funds until exhausting spendable outputs" in {
    val instance = instanceWithOutputs(5)
    instance.block(ExchangeId("1"), 2.5.BTC) should not be 'empty
    instance.block(ExchangeId("2"), 2.BTC) should not be 'empty
    instance.block(ExchangeId("3"), 0.1.BTC) shouldBe 'empty
  }

  it should "unblock previously blocked funds to make them available again" in {
    val instance = instanceWithOutputs(2)
    val Some(id) = instance.block(ExchangeId("1"), 2.BTC)
    instance.unblock(id)
    instance.block(ExchangeId("2"), 2.BTC) should not be 'empty
  }

  it should "allow blocking funds as new outputs are spendable" in {
    val instance = new BlockedOutputs()
    val initialOutputs = buildOutputs(1)
    instance.setSpendCandidates(initialOutputs)
    instance.block(ExchangeId("1"), 3.BTC) shouldBe 'empty
    instance.setSpendCandidates(initialOutputs ++ buildOutputs(2))
    instance.block(ExchangeId("1"), 3.BTC) should not be 'empty
  }

  it should "reject blocking funds for the same id twice" in {
    val instance = new BlockedOutputs()
    instance.setSpendCandidates(buildOutputs(1))
    instance.block(ExchangeId("1"), 1.BTC) should not be 'empty
    instance.block(ExchangeId("1"), 1.BTC) shouldBe 'empty
  }

  it should "reject using funds from an unknown identifier" in {
    val instance = new BlockedOutputs()
    a [UnknownFunds] shouldBe thrownBy {
      instance.use(ExchangeId("unknown"), 2.BTC)
    }
  }

  it should "reject using more funds that previously blocked" in {
    val instance = instanceWithOutputs(2)
    val Some(funds) = instance.block(ExchangeId("1"), 2.BTC)
    a [FundsUseException] shouldBe thrownBy {
      instance.use(funds, 1000.BTC)
    }
  }

  it should "use and cancel usage of outputs" in {
    val instance = instanceWithOutputs(2)
    val funds = ExchangeId("1")
    instance.block(funds, 2.BTC)
    val usedFunds = instance.use(funds, 2.BTC)
    instance.cancelUsage(usedFunds)
    instance.use(funds, 2.BTC) should not be 'empty
  }
}
