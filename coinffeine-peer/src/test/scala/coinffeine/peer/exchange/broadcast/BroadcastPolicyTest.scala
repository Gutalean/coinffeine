package coinffeine.peer.exchange.broadcast

import scala.util.Random

import org.bitcoinj.core.TransactionInput

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}

class BroadcastPolicyTest extends UnitTest {

  "The broadcast policy" should "broadcast the refund transaction if it becomes valid" in new Fixture {
    policy.updateHeight(lockTime - 1)
    expectNotBroadcasting()
    policy.updateHeight(lockTime)
    expectBroadcasting(refund)
  }

  it should "not broadcast when requested but had no offer until reaching the refund block" in
    new Fixture {
      policy.requestPublication()
      expectNotBroadcasting()
      policy.updateHeight(lockTime)
      expectBroadcasting(refund)
    }

  it should "broadcast the last offer when requested" in new Fixture {
    val offer1, offer2 = buildOffer()
    policy.addOfferTransaction(offer1)
    policy.addOfferTransaction(offer2)
    expectNotBroadcasting()
    policy.requestPublication()
    expectBroadcasting(offer2)
  }

  it should "broadcast the last offer when the panic block is reached" in new Fixture {
    val offer = buildOffer()
    policy.addOfferTransaction(offer)
    policy.updateHeight(lockTime - safetyBlocks - 1)
    expectNotBroadcasting()
    policy.updateHeight(lockTime - safetyBlocks)
    expectBroadcasting(offer)
  }

  it should "prefer the last offer over the refund" in new Fixture {
    val offer = buildOffer()
    policy.addOfferTransaction(offer)
    policy.updateHeight(lockTime)
    expectBroadcasting(offer)
  }

  private trait Fixture {
    val safetyBlocks = 10
    val lockTime = 100
    val refund = ImmutableTransaction {
      val tx = new MutableTransaction(CoinffeineUnitTestNetwork)
      tx.setLockTime(100)
      tx
    }
    val policy = new BroadcastPolicyImpl(refund, safetyBlocks)

    def expectNotBroadcasting(): Unit = {
      policy.transactionToBroadcast shouldBe 'empty
    }

    def expectBroadcasting(tx: ImmutableTransaction): Unit = {
      policy.transactionToBroadcast shouldBe Some(tx)
    }

    def buildOffer() = ImmutableTransaction {
      val tx = new MutableTransaction(CoinffeineUnitTestNetwork)
      val input = new TransactionInput(CoinffeineUnitTestNetwork, null, Array.empty)
      input.setSequenceNumber(Random.nextLong().abs)
      tx.addInput(input)
      tx
    }
  }
}
