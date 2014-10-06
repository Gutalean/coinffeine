package coinffeine.peer.bitcoin

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.peer.bitcoin.BlockedOutputs.{BlockingFundsException, UnknownCoinsId}

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
    instance.spendable should be (5.BTC)
    instance.block(2.BTC)
    instance.spendable should be (5.BTC)
  }

  it should "report available funds" in {
    val instance = instanceWithOutputs(5)
    instance.available should be (5.BTC)
    instance.block(2.BTC)
    instance.available should be (3.BTC)
  }

  it should "report blocked funds" in {
    val instance = instanceWithOutputs(5)
    instance.blocked should be (0.BTC)
    instance.block(2.BTC)
    instance.blocked should be (2.BTC)
  }

  it should "block funds until exhausting spendable outputs" in {
    val instance = instanceWithOutputs(5)
    instance.block(2.5.BTC) should not be 'empty
    instance.block(2.BTC) should not be 'empty
    instance.block(0.1.BTC) should be ('empty)
  }

  it should "unblock previously blocked funds to make them available again" in {
    val instance = instanceWithOutputs(2)
    val Some(id) = instance.block(2.BTC)
    instance.unblock(id)
    instance.block(2.BTC) should not be 'empty
  }

  it should "allow blocking funds as new outputs are spendable" in {
    val instance = new BlockedOutputs()
    val initialOutputs = buildOutputs(1)
    instance.setSpendCandidates(initialOutputs)
    instance.block(3.BTC) should be ('empty)
    instance.setSpendCandidates(initialOutputs ++ buildOutputs(2))
    instance.block(3.BTC) should not be 'empty
  }

  it should "reject using funds from an unknown identifier" in {
    val instance = new BlockedOutputs()
    a [UnknownCoinsId] shouldBe thrownBy {
      instance.use(BlockedCoinsId(42), 2.BTC)
    }
  }

  it should "reject using more funds that previously blocked" in {
    val instance = instanceWithOutputs(2)
    val Some(funds) = instance.block(2.BTC)
    a [BlockingFundsException] shouldBe thrownBy {
      instance.use(funds, 1000.BTC)
    }
  }

  it should "use and cancel usage of outputs" in {
    val instance = instanceWithOutputs(2)
    val Some(funds) = instance.block(2.BTC)
    val usedFunds = instance.use(funds, 2.BTC)
    instance.cancelUsage(usedFunds)
    instance.use(funds, 2.BTC) should not be 'empty
  }
}
