package coinffeine.peer.bitcoin

import scala.concurrent.duration._

import akka.actor.{ActorRef, Props}
import akka.testkit._
import org.bitcoinj.core.{FullPrunedBlockChain, PeerGroup}
import org.bitcoinj.store.MemoryFullPrunedBlockStore
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.peer.bitcoin.BitcoinPeerActor.{TransactionNotPublished, Delegates}
import coinffeine.peer.bitcoin.wallet.SmartWallet

class BitcoinPeerActorTest extends AkkaSpec with MockitoSugar with Eventually {

  "The bitcoin peer actor" should "join the bitcoin network" in new Fixture {
    actor ! ServiceActor.Start {}
    expectMsg(ServiceActor.Started)
  }

  it should "retrieve the blockchain actor" in new Fixture {
    actor ! ServiceActor.Start {}
    actor ! BitcoinPeerActor.RetrieveBlockchainActor
    expectMsg(ServiceActor.Started)
    expectMsg(BitcoinPeerActor.BlockchainActorRef(blockchainActor.ref))
  }

  it should "retrieve the wallet actor" in new Fixture {
    actor ! ServiceActor.Start {}
    actor ! BitcoinPeerActor.RetrieveWalletActor
    expectMsg(ServiceActor.Started)
    expectMsg(BitcoinPeerActor.WalletActorRef(walletActor.ref))
  }

  it should "be stopped" in new Fixture {
    actor ! ServiceActor.Start {}
    actor ! ServiceActor.Stop
    fishForMessage(hint = "should actually stop") {
      case ServiceActor.Stopped => true
      case _ => false
    }
  }

  it should "delegate transaction publication" in new Fixture {
    actor ! ServiceActor.Start {}
    expectMsg(ServiceActor.Started)
    val dummyTx = ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))
    actor ! BitcoinPeerActor.PublishTransaction(dummyTx)
    val notYetConnected = receiveWhile(max = 1.second.dilated) {
      case TransactionNotPublished(_, cause) if cause.getMessage.contains("Not connected") =>
    }.nonEmpty
    if (notYetConnected) {
      actor ! BitcoinPeerActor.PublishTransaction(dummyTx)
    }
    transactionPublisher.expectCreation()
  }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    def connectionRetryInterval = 1.minute
    val peerGroup = new PeerGroup(network)
    val blockchainActor, walletActor, transactionPublisher = new MockSupervisedActor()
    val wallet = new SmartWallet(network)
    val blockchain = new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, 1000))
    val properties = new MutableNetworkProperties

    blockchain.addWallet(wallet.delegate)
    peerGroup.addWallet(wallet.delegate)

    val actor = system.actorOf(Props(new BitcoinPeerActor(properties,
      peerGroup,
      new Delegates {
        override def transactionPublisher(tx: ImmutableTransaction, listener: ActorRef): Props =
          Fixture.this.transactionPublisher.props
        override val walletActor = Fixture.this.walletActor.props
        override val blockchainActor = Fixture.this.blockchainActor.props
      },
      blockchain, connectionRetryInterval)))
    walletActor.expectCreation()
    blockchainActor.expectCreation()
  }
}
