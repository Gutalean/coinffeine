package coinffeine.peer.exchange

import scala.concurrent.duration.Duration

import akka.actor._
import akka.pattern._
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.exchange.Exchange
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.payment.okpay.{VerificationStatus, OkPaySettings}

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

    class TestActor(wallet: ActorRef, settings: OkPaySettingsStub) extends Actor {
      import context.dispatcher
      val instance = new PeerInfoLookupImpl(wallet, settings.lookup)
      override def receive: Receive = {
        case "retrieve" => instance.lookup().pipeTo(sender())
      }
    }

    val wallet = TestProbe()
    val settingsProvider = new OkPaySettingsStub
    val peerInfo = Exchange.PeerInfo("FooPay0001", new KeyPair())
    val actor = system.actorOf(Props(new TestActor(wallet.ref, settingsProvider)))

    def givenConfiguredAccountId(): Unit = {
      settingsProvider.givenAccountId(peerInfo.paymentProcessorAccount)
    }

    def givenNotConfiguredAccountId(): Unit = {
      settingsProvider.givenUndefinedAccountId()
    }

    def whenLookupIsRequested(): Unit = {
      actor ! "retrieve"
    }

    def expectSuccessfulKeyPairRetrieval(): Unit = {
      wallet.expectMsg(WalletActor.CreateKeyPair)
      wallet.reply(WalletActor.KeyPairCreated(peerInfo.bitcoinKey))
    }
  }

  private class OkPaySettingsStub {
    private var settings: OkPaySettings = OkPaySettings(
      userAccount = None,
      seedToken = None,
      verificationStatus = None,
      serverEndpointOverride = None,
      pollingInterval = Duration.Zero
    )

    def lookup(): OkPaySettings = settings

    def givenUndefinedAccountId(): Unit = {
      settings = settings.copy(userAccount = None)
    }

    def givenAccountId(id: AccountId): Unit = {
      settings = settings.copy(userAccount = Some(id))
    }
  }
}
