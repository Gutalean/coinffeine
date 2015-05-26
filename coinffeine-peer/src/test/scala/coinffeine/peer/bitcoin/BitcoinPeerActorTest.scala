package coinffeine.peer.bitcoin

import scala.concurrent.duration._

import akka.actor.{ActorRef, Props}
import akka.testkit._
import org.bitcoinj.core.PeerGroup
import org.scalatest.concurrent.Eventually

import coinffeine.common.akka.Service
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.peer.bitcoin.BitcoinPeerActor.{Delegates, TransactionNotPublished}
import coinffeine.peer.bitcoin.platform.{BitcoinPlatform, DefaultBitcoinPlatformBuilder}
import coinffeine.peer.bitcoin.wallet.SmartWallet

class BitcoinPeerActorTest extends AkkaSpec with Eventually {

  private val dummyTx = ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))

  "The bitcoin peer actor" should "join the bitcoin network" in new Fixture {
    actor ! Service.Start {}
    expectMsg(Service.Started)
  }

  it should "retrieve the blockchain actor" in new Fixture {
    givenStartedActor()
    actor ! BitcoinPeerActor.RetrieveBlockchainActor
    expectMsg(BitcoinPeerActor.BlockchainActorRef(blockchainActor.ref))
  }

  it should "retrieve the wallet actor" in new Fixture {
    givenStartedActor()
    actor ! BitcoinPeerActor.RetrieveWalletActor
    expectMsg(BitcoinPeerActor.WalletActorRef(walletActor.ref))
  }

  it should "be stopped" in new Fixture {
    givenStartedActor()
    actor ! Service.Stop
    fishForMessage(hint = "should actually stop") {
      case Service.Stopped => true
      case _ => false
    }
  }

  it should "delegate transaction publication" in new Fixture {
    givenStartedActor()
    actor ! BitcoinPeerActor.PublishTransaction(dummyTx)
    val notYetConnected = receiveWhile(max = 1.second.dilated) {
      case TransactionNotPublished(_, cause) if cause.getMessage.contains("Not connected") =>
    }.nonEmpty
    if (notYetConnected) {
      actor ! BitcoinPeerActor.PublishTransaction(dummyTx)
    }
    transactionPublisher.expectCreation()
  }

  it should "spawn no more than one transaction publisher for the same transaction" in
    new Fixture {
      givenStartedActor()
      actor ! BitcoinPeerActor.PublishTransaction(dummyTx)
      val notYetConnected = receiveWhile(max = 1.second.dilated) {
        case TransactionNotPublished(_, cause) if cause.getMessage.contains("Not connected") =>
      }.nonEmpty
      actor ! BitcoinPeerActor.PublishTransaction(dummyTx)
      actor ! BitcoinPeerActor.PublishTransaction(dummyTx)
      actor ! BitcoinPeerActor.PublishTransaction(dummyTx)

      transactionPublisher.expectCreation()
      transactionPublisher.expectNoMsg()
    }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    protected def connectionRetryInterval = 1.minute
    protected val blockchainActor, walletActor, transactionPublisher = new MockSupervisedActor()

    private val properties = new MutableNetworkProperties
    private val platformBuilder = new DefaultBitcoinPlatformBuilder().setNetwork(network)

    protected val actor = system.actorOf(Props(new BitcoinPeerActor(properties,
      new Delegates {
        override def transactionPublisher(
            peerGroup: PeerGroup, tx: ImmutableTransaction, listener: ActorRef): Props =
          Fixture.this.transactionPublisher.props(peerGroup, tx, listener)
        override def walletActor(wallet: SmartWallet) = Fixture.this.walletActor.props(wallet)
        override def blockchainActor(platform: BitcoinPlatform) =
          Fixture.this.blockchainActor.props(platform)
      },
      platformBuilder,
      this,
      connectionRetryInterval
    )))
    walletActor.expectCreation()
    blockchainActor.expectCreation()

    protected def givenStartedActor(): Unit = {
      actor ! Service.Start {}
      expectMsg(Service.Started)
    }
  }
}
