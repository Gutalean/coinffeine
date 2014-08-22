package coinffeine.peer.exchange

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.google.bitcoin.core.TransactionInput
import com.google.bitcoin.crypto.TransactionSignature
import com.google.bitcoin.script.ScriptBuilder

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.BlockchainActor.{BlockchainHeightReached, WatchBlockchainHeight}
import coinffeine.peer.exchange.TransactionBroadcastActor._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.LastBroadcastableOffer
import coinffeine.peer.exchange.test.CoinffeineClientTest

class TransactionBroadcastActorTest extends CoinffeineClientTest("txBroadcastTest") {

  private val refundLockTime = 20
  private val refundTx = ImmutableTransaction {
    val tx = new MutableTransaction(network)
    tx.setLockTime(refundLockTime)
    val input = new TransactionInput(
      network,
      null, // parent transaction
      ScriptBuilder.createInputScript(TransactionSignature.dummy()).getProgram)
    input.setSequenceNumber(0)
    tx.addInput(input)
    tx
  }
  private val someLastOffer = ImmutableTransaction(new MutableTransaction(network))
  private val protocolConstants = ProtocolConstants()
  private val panicBlock = refundLockTime - protocolConstants.refundSafetyBlockCount

  "An exchange transaction broadcast actor" should "broadcast the refund transaction if it " +
    "becomes valid" in new Fixture {
      instance ! StartBroadcastHandling(refundTx, peerActor.ref, Set(self))
      givenSuccessfulBlockchainRetrieval()
      givenPanicNotification()
      val broadcastReadyRequester = expectBroadcastReadinessRequest(refundLockTime)
      blockchain.send(broadcastReadyRequester, BlockchainHeightReached(refundLockTime))
      val result = givenSuccessfulBroadcast(refundTx)
      expectMsg(ExchangeFinished(result))
      system.stop(instance)
    }

  it should "broadcast the refund transaction if it receives a finish exchange signal" in new Fixture {
    instance ! StartBroadcastHandling(refundTx, peerActor.ref, Set(self))
    givenSuccessfulBlockchainRetrieval()
    expectPanicNotificationRequest()
    instance ! FinishExchange
    val broadcastReadyRequester = expectBroadcastReadinessRequest(refundLockTime)
    blockchain.send(broadcastReadyRequester, BlockchainHeightReached(refundLockTime))
    val result = givenSuccessfulBroadcast(refundTx)
    expectMsg(ExchangeFinished(result))
    system.stop(instance)
  }

  it should "broadcast the last offer when the refund transaction becomes valid" in new Fixture {
    instance ! StartBroadcastHandling(refundTx, peerActor.ref, Set(self))
    givenSuccessfulBlockchainRetrieval()
    givenLastOffer(someLastOffer)
    givenPanicNotification()

    val result = givenSuccessfulBroadcast(someLastOffer)
    expectMsg(ExchangeFinished(result))
    system.stop(instance)
  }

  it should "broadcast the refund transaction if there is no last offer" in new Fixture {
    instance ! StartBroadcastHandling(refundTx, peerActor.ref, Set(self))
    givenSuccessfulBlockchainRetrieval()
    expectPanicNotificationRequest()
    instance ! FinishExchange
    val broadcastReadyRequester = expectBroadcastReadinessRequest(refundLockTime)
    blockchain.send(broadcastReadyRequester, BlockchainHeightReached(refundLockTime))

    val result = givenSuccessfulBroadcast(refundTx)
    expectMsg(ExchangeFinished(result))
    system.stop(instance)
  }

  trait Fixture {
    val peerActor = TestProbe()
    val blockchain = TestProbe()
    val instance: ActorRef = system.actorOf(Props(new TransactionBroadcastActor(protocolConstants)))

    def givenSuccessfulBlockchainRetrieval(): Unit = {
      peerActor.expectMsg(RetrieveBlockchainActor)
      peerActor.reply(BlockchainActorRef(blockchain.ref))
    }

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
