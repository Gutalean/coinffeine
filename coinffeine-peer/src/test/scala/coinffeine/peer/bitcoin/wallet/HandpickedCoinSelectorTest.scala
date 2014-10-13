package coinffeine.peer.bitcoin.wallet

import scala.collection.JavaConversions._

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{KeyPair, MutableTransactionOutput}
import coinffeine.model.currency._

class HandpickedCoinSelectorTest extends UnitTest {

  "A handpicked coins selector" should "select outputs up to the requested amount" in new Fixture {
    val selection = selector.select(3.BTC, candidates = eligibleOutputs.toList)
    selection.valueGathered.value shouldBe 3.BTC.units
    selection.gathered.toSet.subsetOf(eligibleOutputs) shouldBe true
  }

  it should "ignore non eligible outputs" in new Fixture {
    private val nonEligibleOutputs = List.fill(10)(outputOf(1.BTC))
    val selection = selector.select(4.BTC, candidates = nonEligibleOutputs ++ eligibleOutputs)
    selection.gathered.toSet shouldBe eligibleOutputs
  }

  private trait Fixture {
    val eligibleOutputs = Set(outputOf(1.BTC), outputOf(2.BTC), outputOf(1.BTC))
    val selector = new HandpickedCoinSelector(eligibleOutputs)

    def outputOf(value: Bitcoin.Amount): MutableTransactionOutput = {
      new MutableTransactionOutput(CoinffeineUnitTestNetwork, null, value, new KeyPair)
    }
  }
}
