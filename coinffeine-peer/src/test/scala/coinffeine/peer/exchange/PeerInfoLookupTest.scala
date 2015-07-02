package coinffeine.peer.exchange

import akka.actor._
import akka.pattern._
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.exchange.Exchange
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.bitcoin.wallet.WalletActor

class PeerInfoLookupTest extends AkkaSpec {

  "Looking peer info up" should "retrieve wallet id and a fresh key pair" in new Fixture {
    givenConfiguredAccountId()
    whenLookupIsRequested()
    expectSuccessfulKeyPairRetrieval()
    expectMsg(peerInfo)
  }

  it should "fail if wallet actor doesn't respond" in new Fixture {
    givenConfiguredAccountId()
    whenLookupIsRequested()
    expectMsgType[Status.Failure].cause.getMessage should include ("Cannot get a fresh key pair")
  }

  it should "fail if account id is not configured" in new Fixture {
    givenNotConfiguredAccountId()
    whenLookupIsRequested()
    expectSuccessfulKeyPairRetrieval()
    expectMsgType[Status.Failure].cause.getMessage should include (
      "missing OKPay wallet ID in app config")
  }

  private trait Fixture {

    class TestActor(wallet: ActorRef, settings: SettingsStub) extends Actor {
      import context.dispatcher
      val instance = new PeerInfoLookupImpl(wallet, settings.lookup())
      override def receive: Receive = {
        case "retrieve" => instance.lookup().pipeTo(sender())
      }
    }

    val wallet = TestProbe()
    val settings = new SettingsStub
    val peerInfo = Exchange.PeerInfo("FooPay0001", new KeyPair())
    val actor = system.actorOf(Props(new TestActor(wallet.ref, settings)))

    def givenConfiguredAccountId(): Unit = {
      settings.givenAccountId(peerInfo.paymentProcessorAccount)
    }

    def givenNotConfiguredAccountId(): Unit = {
      settings.givenUndefinedAccountId()
    }

    def whenLookupIsRequested(): Unit = {
      actor ! "retrieve"
    }

    def expectSuccessfulKeyPairRetrieval(): Unit = {
      wallet.expectMsg(WalletActor.CreateKeyPair)
      wallet.reply(WalletActor.KeyPairCreated(peerInfo.bitcoinKey))
    }
  }

  private class SettingsStub {
    private var value: Option[AccountId] = _

    def lookup(): Option[AccountId] = value

    def givenUndefinedAccountId(): Unit = {
      value = None
    }

    def givenAccountId(id: AccountId): Unit = {
      value = Some(id)
    }
  }
}
