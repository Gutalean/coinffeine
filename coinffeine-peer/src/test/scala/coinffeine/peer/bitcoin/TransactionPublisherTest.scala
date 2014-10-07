package coinffeine.peer.bitcoin

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.actor.{ActorRef, Props}
import akka.testkit._
import org.scalatest.OptionValues

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}

class TransactionPublisherTest extends AkkaSpec("transactionPublisher") with OptionValues {

  "A transaction publisher" should "notify successful publication" in new Fixture {
    broadcaster.givenSuccessOnTransactionBroadcast()
    spawnPublisher()
    listener.expectMsg(BitcoinPeerActor.TransactionPublished(tx, tx))
    broadcaster.lastBroadcast.value shouldBe tx.get
    listener.expectTerminated(publisher)
  }

  it should "retry on publication error" in new Fixture {
    broadcaster.givenTemporaryErrorOnTransactionBroadcast(cause)
    spawnPublisher()
    listener.expectMsg(BitcoinPeerActor.TransactionPublished(tx, tx))
    broadcaster.lastBroadcast.value shouldBe tx.get
  }

  it should "report publication error when the problem persists" in new Fixture {
    broadcaster.givenErrorOnTransactionBroadcast(cause)
    spawnPublisher()
    listener.expectMsg(BitcoinPeerActor.TransactionNotPublished(tx, cause))
    broadcaster.lastBroadcast shouldBe 'empty
    listener.expectTerminated(publisher)
  }

  it should "start a new attempt if the previous one takes more than a timeout" in new Fixture {
    broadcaster.givenNoResponseOnTransactionBroadcast()
    spawnPublisher()
    expectNoMsg(rebroadcastTimeout)
    broadcaster.givenSuccessOnTransactionBroadcast()
    listener.expectMsg(BitcoinPeerActor.TransactionPublished(tx, tx))
  }

  trait Fixture {
    val broadcaster = new MockTransactionBroadcaster()
    val listener = TestProbe()
    val tx = ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))
    val cause = new Exception("provoked error") with NoStackTrace
    val rebroadcastTimeout = 1.second
    var publisher: ActorRef = _

    def spawnPublisher(): Unit = {
      publisher = system.actorOf(Props(
        new TransactionPublisher(tx, broadcaster, listener.ref, rebroadcastTimeout.dilated)))
      listener.watch(publisher)
    }
  }
}
