package coinffeine.peer.exchange.broadcast

import scala.concurrent.duration._
import scala.util.Random

import akka.testkit._
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.script.ScriptBuilder

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction, TransactionSignature}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.broadcast.TransactionBroadcaster._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.LastBroadcastableOffer
import coinffeine.peer.exchange.test.CoinffeineClientTest

class TransactionBroadcasterTest extends CoinffeineClientTest("txBroadcastTest") {

  private val refundLockTime = 20
  private val someLastOffer = ImmutableTransaction(new MutableTransaction(network))
  private val idleTime = 100.millis

  "A persistent transaction broadcast actor" should
    "broadcast the refund transaction if it becomes valid" in new Fixture {
      expectRelevantHeightsSubscription()
      givenHeightNotification(panicBlock)
      givenHeightNotification(refundLockTime)
      givenSuccessfulBroadcast(refundTx)
      expectTermination()
    }

  it should "broadcast the refund transaction if it receives a finish exchange signal" in
    new Fixture {
      expectRelevantHeightsSubscription()
      instance ! PublishBestTransaction
      givenHeightNotification(refundLockTime)
      givenSuccessfulBroadcast(refundTx)
      expectTermination()
    }

  it should "broadcast the last offer when the refund transaction is about to become valid" in
    new Fixture {
      expectRelevantHeightsSubscription()
      givenLastOffer(someLastOffer)
      givenHeightNotification(panicBlock)
      givenSuccessfulBroadcast(someLastOffer)
      expectTermination()
    }

  it should "broadcast the refund transaction if there is no last offer" in new Fixture {
    expectRelevantHeightsSubscription()
    instance ! PublishBestTransaction
    givenHeightNotification(refundLockTime)
    givenSuccessfulBroadcast(refundTx)
    expectTermination()
  }

  it should "broadcast the refund transaction if requested" in new Fixture {
    expectRelevantHeightsSubscription()
    instance ! PublishRefundTransaction
    givenHeightNotification(refundLockTime)
    givenSuccessfulBroadcast(refundTx)
    expectTermination()
  }

  it should "broadcast the refund transaction if requested even with previous last offer" in new Fixture {
    expectRelevantHeightsSubscription()
    givenLastOffer(someLastOffer)
    instance ! PublishRefundTransaction
    givenHeightNotification(refundLockTime)
    givenSuccessfulBroadcast(refundTx)
    expectTermination()
  }

  it should "broadcast again and again after the refund block" in new Fixture {
    override def retryInterval = 100.millis
    expectRelevantHeightsSubscription()
    givenHeightNotification(refundLockTime)

    givenSuccessfulBroadcast(refundTx)
    givenSuccessfulBroadcast(refundTx)
    givenSuccessfulBroadcast(refundTx)

    expectTermination()
  }

  it should "broadcast again and again after panicking" in new Fixture {
    override def retryInterval = 100.millis
    expectRelevantHeightsSubscription()
    givenLastOffer(someLastOffer)
    givenHeightNotification(panicBlock)

    givenSuccessfulBroadcast(someLastOffer)
    givenSuccessfulBroadcast(someLastOffer)
    givenSuccessfulBroadcast(someLastOffer)

    expectTermination()
  }

  it should "broadcast again and again after a publication request" in new Fixture {
    override def retryInterval = 100.millis
    expectRelevantHeightsSubscription()
    givenLastOffer(someLastOffer)
    instance ! PublishBestTransaction

    givenSuccessfulBroadcast(someLastOffer)
    givenSuccessfulBroadcast(someLastOffer)
    givenSuccessfulBroadcast(someLastOffer)

    expectTermination()
  }

  it should "broadcast again and again after a refund publication request" in new Fixture {
    override def retryInterval = 100.millis
    expectRelevantHeightsSubscription()
    givenLastOffer(someLastOffer)
    instance ! PublishBestTransaction

    givenSuccessfulBroadcast(someLastOffer)
    givenSuccessfulBroadcast(someLastOffer)
    givenSuccessfulBroadcast(someLastOffer)

    instance ! PublishRefundTransaction

    givenSuccessfulBroadcast(refundTx)
    givenSuccessfulBroadcast(refundTx)
    givenSuccessfulBroadcast(refundTx)

    expectTermination()
  }

  it should "persist its state" in new Fixture {
    givenLastOffer(someLastOffer)
    expectNoMsg(idleTime)
    instance ! PublishBestTransaction
    expectNoMsg(idleTime)
    system.stop(instance)
  }

  it should "restore its state" in new Fixture {
    override def useLastPersistenceId = true
    expectRelevantHeightsSubscription()

    givenSuccessfulBroadcast(someLastOffer)
    expectTermination()
    expectNoMsg(idleTime)
  }

  it should "delete its journal after being finished" in new Fixture {
    override def useLastPersistenceId = true
    override def retryInterval = idleTime / 2
    expectNoMsg(idleTime) // Must not retry publication
  }

  it should "persist its state before broadcasting a transaction" in new Fixture {
    expectRelevantHeightsSubscription()
    givenLastOffer(someLastOffer)
    expectNoMsg(idleTime)
    system.stop(instance)
  }

  it should "broadcast the last offer if panicked before even starting" in new Fixture {
    override def useLastPersistenceId = true
    expectRelevantHeightsSubscription()
    givenHeightNotification(panicBlock + 100)
    givenSuccessfulBroadcast(someLastOffer)
    expectTermination()
  }

  // Last refund transaction is saved to allow testing the persistence
  var lastRefundTx: ImmutableTransaction = _

  trait Fixture {
    protected def useLastPersistenceId: Boolean = false
    protected def retryInterval: FiniteDuration = 1.minute
    protected val protocolConstants =
      ProtocolConstants(transactionRepublicationInterval = retryInterval.dilated)
    protected val panicBlock = refundLockTime - protocolConstants.refundSafetyBlockCount
    protected val refundTx =
      if (useLastPersistenceId) lastRefundTx
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
    protected val instance = system.actorOf(TransactionBroadcaster.props(
      refundTx, Collaborators(bitcoinPeer.ref, blockchain.ref), protocolConstants))
    terminationListener.watch(instance)

    protected def expectRelevantHeightsSubscription(): Unit = {
      blockchain.expectMsgAllOf(
        BlockchainActor.WatchBlockchainHeight(panicBlock),
        BlockchainActor.WatchBlockchainHeight(refundLockTime),
        BlockchainActor.RetrieveBlockchainHeight
      )
    }

    protected def givenSuccessfulBroadcast(tx: ImmutableTransaction): Unit = {
      bitcoinPeer.expectMsg(PublishTransaction(tx))
      bitcoinPeer.reply(TransactionPublished(tx, tx))
    }

    protected def givenHeightNotification(height: Long): Unit = {
      blockchain.reply(BlockchainActor.BlockchainHeightReached(height))
    }

    protected def givenLastOffer(offer: ImmutableTransaction): Unit = {
      instance ! LastBroadcastableOffer(offer)
    }

    protected def expectTermination(): Unit = {
      instance ! TransactionBroadcaster.Finish
      terminationListener.expectTerminated(instance)
    }
  }
}
