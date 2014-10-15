package coinffeine.peer.exchange.broadcast

import scala.util.Random
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit._
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.script.ScriptBuilder

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction, TransactionSignature}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor.{BlockchainHeightReached, WatchBlockchainHeight}
import coinffeine.peer.exchange.broadcast.DefaultExchangeTransactionBroadcaster.Collaborators
import coinffeine.peer.exchange.broadcast.ExchangeTransactionBroadcaster._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.LastBroadcastableOffer
import coinffeine.peer.exchange.test.CoinffeineClientTest

class DefaultExchangeTransactionBroadcasterTest extends CoinffeineClientTest("txBroadcastTest") {

  private val refundLockTime = 20
  private val someLastOffer = ImmutableTransaction(new MutableTransaction(network))
  private val protocolConstants = ProtocolConstants()
  private val panicBlock = refundLockTime - protocolConstants.refundSafetyBlockCount

  "An exchange transaction broadcast actor" should
    "broadcast the refund transaction if it becomes valid" in new Fixture {
      givenPanicNotification()
      val broadcastReadyRequester = expectBroadcastReadinessRequest(refundLockTime)
      blockchain.send(broadcastReadyRequester, BlockchainHeightReached(refundLockTime))
      val result = givenSuccessfulBroadcast(refundTx)
      expectMsg(SuccessfulBroadcast(result))
      terminationListener.expectTerminated(instance)
    }

  it should "broadcast the refund transaction if it receives a finish exchange signal" in
    new Fixture {
      expectPanicNotificationRequest()
      instance ! PublishBestTransaction
      val broadcastReadyRequester = expectBroadcastReadinessRequest(refundLockTime)
      blockchain.send(broadcastReadyRequester, BlockchainHeightReached(refundLockTime))
      val result = givenSuccessfulBroadcast(refundTx)
      expectMsg(SuccessfulBroadcast(result))
      terminationListener.expectTerminated(instance)
    }

  it should "broadcast the last offer when the refund transaction is about to become valid" in
    new Fixture {
      givenLastOffer(someLastOffer)
      givenPanicNotification()

      val result = givenSuccessfulBroadcast(someLastOffer)
      expectMsg(SuccessfulBroadcast(result))
      terminationListener.expectTerminated(instance)
    }

  it should "broadcast the refund transaction if there is no last offer" in new Fixture {
    expectPanicNotificationRequest()
    instance ! PublishBestTransaction
    val broadcastReadyRequester = expectBroadcastReadinessRequest(refundLockTime)
    blockchain.send(broadcastReadyRequester, BlockchainHeightReached(refundLockTime))

    val result = givenSuccessfulBroadcast(refundTx)
    expectMsg(SuccessfulBroadcast(result))
    terminationListener.expectTerminated(instance)
  }

  it should "persist its state" in new Fixture {
    givenLastOffer(someLastOffer)
    expectNoMsg(100.millis.dilated)
    system.stop(instance)
  }

  it should "restore its state" in new Fixture {
    override def useLastRefundTx = true
    expectPanicNotificationRequest()

    instance ! PublishBestTransaction

    givenSuccessfulBroadcast(someLastOffer)
    expectMsgType[SuccessfulBroadcast]
    terminationListener.expectTerminated(instance)
  }

  // Last refund transaction is saved to allow testing the persistence
  var lastRefundTx: ImmutableTransaction = _

  trait Fixture {
    def useLastRefundTx: Boolean = false
    val refundTx =
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
    val peerActor, blockchain, terminationListener = TestProbe()
    val instance = system.actorOf(DefaultExchangeTransactionBroadcaster.props(
      refundTx, Collaborators(peerActor.ref, blockchain.ref, listener = self), protocolConstants))
    terminationListener.watch(instance)

    def expectPanicNotificationRequest(): Unit = {
      blockchain.expectMsg(WatchBlockchainHeight(panicBlock))
    }

    def expectBroadcastReadinessRequest(block: Int): ActorRef = {
      blockchain.expectMsg(WatchBlockchainHeight(block))
      blockchain.sender()
    }

    def givenSuccessfulBroadcast(tx: ImmutableTransaction): TransactionPublished = {
      peerActor.expectMsg(PublishTransaction(tx))
      val result = TransactionPublished(tx, tx)
      peerActor.reply(result)
      result
    }

    def givenPanicNotification(): Unit = {
      expectPanicNotificationRequest()
      blockchain.reply(BlockchainHeightReached(panicBlock))
    }

    def givenLastOffer(offer: ImmutableTransaction): Unit = {
      instance ! LastBroadcastableOffer(offer)
    }
  }
}
