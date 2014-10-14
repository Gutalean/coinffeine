package coinffeine.peer.exchange

import akka.actor._
import akka.pattern._
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.exchange.Exchange
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.payment.PaymentProcessorActor

class DefaultPeerInfoLookupTest extends AkkaSpec {

  "Looking peer info up" should "retrieve wallet id and a fresh key pair" in new Fixture {
    actor ! "retrieve"
    givenSuccessfulKeyPairRetrieval()
    givenSuccessfulAccountIdRetrieval()
    expectMsg(peerInfo)
  }

  it should "fail if wallet actor doesn't respond" in new Fixture {
    actor ! "retrieve"
    givenSuccessfulAccountIdRetrieval()
    expectMsgType[Status.Failure].cause.getMessage should include ("Cannot get a fresh key pair")
  }

  it should "fail if payment processor doesn't respond" in new Fixture {
    actor ! "retrieve"
    givenSuccessfulKeyPairRetrieval()
    expectMsgType[Status.Failure].cause.getMessage should include (
      "Cannot retrieve the user account id")
  }

  trait Fixture {
    class TestActor(wallet: ActorRef, paymentProcessor: ActorRef) extends Actor {
      import context.dispatcher
      val instance = new DefaultPeerInfoLookup(wallet, paymentProcessor)
      override def receive: Receive = {
        case "retrieve" => instance.lookup().pipeTo(sender())
      }
    }

    val wallet, paymentProcessor = TestProbe()
    val peerInfo = Exchange.PeerInfo("FooPay0001", new KeyPair())
    val actor = system.actorOf(Props(new TestActor(wallet.ref, paymentProcessor.ref)))

    def givenSuccessfulKeyPairRetrieval(): Unit = {
      wallet.expectMsg(WalletActor.CreateKeyPair)
      wallet.reply(WalletActor.KeyPairCreated(peerInfo.bitcoinKey))
    }

    def givenSuccessfulAccountIdRetrieval(): Unit = {
      paymentProcessor.expectMsg(PaymentProcessorActor.RetrieveAccountId)
      paymentProcessor.reply(PaymentProcessorActor.RetrievedAccountId(
        peerInfo.paymentProcessorAccount))
    }
  }
}
