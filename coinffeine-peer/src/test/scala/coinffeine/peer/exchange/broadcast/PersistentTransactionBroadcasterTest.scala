package coinffeine.peer.exchange.broadcast

import scala.util.Random
import scala.concurrent.duration._

import akka.testkit._
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.script.ScriptBuilder

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction, TransactionSignature}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.broadcast.PersistentTransactionBroadcaster.Collaborators
import coinffeine.peer.exchange.broadcast.TransactionBroadcaster._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.LastBroadcastableOffer
import coinffeine.peer.exchange.test.CoinffeineClientTest

class PersistentTransactionBroadcasterTest extends CoinffeineClientTest("txBroadcastTest") {

  private val refundLockTime = 20
  private val someLastOffer = ImmutableTransaction(new MutableTransaction(network))
  private val protocolConstants = ProtocolConstants()
  private val panicBlock = refundLockTime - protocolConstants.refundSafetyBlockCount

  "A persistent transaction broadcast actor" should
    "broadcast the refund transaction if it becomes valid" in new Fixture {
      expectRelevantHeightsSubscription()
      givenHeightNotification(panicBlock)
      givenHeightNotification(refundLockTime)
      val result = givenSuccessfulBroadcast(refundTx)
      expectTerminationWithResult(SuccessfulBroadcast(result))
    }

  it should "broadcast the refund transaction if it receives a finish exchange signal" in
    new Fixture {
      expectRelevantHeightsSubscription()
      instance ! PublishBestTransaction
      givenHeightNotification(refundLockTime)
      val result = givenSuccessfulBroadcast(refundTx)
      expectTerminationWithResult(SuccessfulBroadcast(result))
    }

  it should "broadcast the last offer when the refund transaction is about to become valid" in
    new Fixture {
      expectRelevantHeightsSubscription()
      givenLastOffer(someLastOffer)
      givenHeightNotification(panicBlock)
      val result = givenSuccessfulBroadcast(someLastOffer)
      expectTerminationWithResult(SuccessfulBroadcast(result))
    }

  it should "broadcast the refund transaction if there is no last offer" in new Fixture {
    expectRelevantHeightsSubscription()
    instance ! PublishBestTransaction
    givenHeightNotification(refundLockTime)
    val result = givenSuccessfulBroadcast(refundTx)
    expectTerminationWithResult(SuccessfulBroadcast(result))
  }

  it should "persist its state" in new Fixture {
    givenLastOffer(someLastOffer)
    expectNoMsg(100.millis.dilated)
    instance ! PublishBestTransaction
    system.stop(instance)
  }

  it should "restore its state" in new Fixture {
    override def useLastRefundTx = true
    expectRelevantHeightsSubscription()

    val result = givenSuccessfulBroadcast(someLastOffer)
    expectTerminationWithResult(SuccessfulBroadcast(result))
    expectNoMsg(100.millis.dilated)
  }

  it should "delete its journal after being finished" in new Fixture {
    override def useLastRefundTx = true
    expectNoMsg() // Must not remember SuccessfulBroadcast(result)
  }

  // Last refund transaction is saved to allow testing the persistence
  var lastRefundTx: ImmutableTransaction = _

  trait Fixture {
    protected def useLastRefundTx: Boolean = false
    protected val refundTx =
      if (useLastRefundTx) lastRefundTx
      else ImmutableTransaction {
        val tx = new MutableTransaction(network)
        tx.setLockTime(refundLockTime)
        val input = new TransactionInput(
          network,
          null, // parent transaction
          ScriptBuilder.createInputScript(TransactionSignature.dummy).getProgram)
        input.setSequenceNumber(Random.nextLong().abs)
        tx.addInput(input)
        tx
      }
    lastRefundTx = refundTx
    protected val bitcoinPeer, blockchain, terminationListener = TestProbe()
    protected val instance = system.actorOf(PersistentTransactionBroadcaster.props(
      refundTx, Collaborators(bitcoinPeer.ref, blockchain.ref, listener = self), protocolConstants))
    terminationListener.watch(instance)

    protected def expectRelevantHeightsSubscription(): Unit = {
      blockchain.expectMsgAllOf(
        BlockchainActor.WatchBlockchainHeight(panicBlock),
        BlockchainActor.WatchBlockchainHeight(refundLockTime),
        BlockchainActor.RetrieveBlockchainHeight
      )
    }

    protected def givenSuccessfulBroadcast(tx: ImmutableTransaction): TransactionPublished = {
      bitcoinPeer.expectMsg(PublishTransaction(tx))
      val result = TransactionPublished(tx, tx)
      bitcoinPeer.reply(result)
      result
    }

    protected def givenHeightNotification(height: Long): Unit = {
      blockchain.reply(BlockchainActor.BlockchainHeightReached(height))
    }

    protected def givenLastOffer(offer: ImmutableTransaction): Unit = {
      instance ! LastBroadcastableOffer(offer)
    }

    protected def expectTerminationWithResult(broadcastResult: BroadcastResult): Unit = {
      expectMsg(broadcastResult)
      instance ! TransactionBroadcaster.Finish
      terminationListener.expectTerminated(instance)
    }
  }
}
