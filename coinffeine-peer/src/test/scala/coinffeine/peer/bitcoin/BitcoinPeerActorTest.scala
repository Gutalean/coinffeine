package coinffeine.peer.bitcoin

import scala.concurrent.duration._

import akka.actor.Props
import com.google.bitcoin.core.{FullPrunedBlockChain, PeerGroup}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.event.BitcoinConnectionStatus.NotDownloading
import coinffeine.model.event.{BitcoinConnectionStatus, EventChannelProbe}

class BitcoinPeerActorTest extends AkkaSpec with MockitoSugar {

  "The bitcoin peer actor" should "join the bitcoin network" in
    new Fixture {
      eventChannelProbe.expectMsg(BitcoinConnectionStatus(0, NotDownloading))
      actor ! BitcoinPeerActor.JoinBitcoinNetwork
      blockchainActor.expectMsg(BlockchainActor.Initialize(blockchain))
    }

  it should "retrieve connection status on demand" in new Fixture {
    actor ! BitcoinPeerActor.RetrieveConnectionStatus
    expectMsg(BitcoinConnectionStatus(activePeers = 0, NotDownloading))
  }

  it should "retrieve the blockchain actor" in new Fixture {
    actor ! BitcoinPeerActor.RetrieveBlockchainActor
    expectMsg(BitcoinPeerActor.BlockchainActorRef(blockchainActor.ref))
  }

  it should "retrieve the wallet actor" in new Fixture {
    actor ! BitcoinPeerActor.RetrieveWalletActor
    expectMsg(BitcoinPeerActor.WalletActorRef(walletActor.ref))
  }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    def connectionRetryInterval = 1.minute
    val peerGroup = new PeerGroup(network)
    val blockchainActor, walletActor = new MockSupervisedActor()
    val eventChannelProbe = EventChannelProbe()
    val blockchain = new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, 1000))
    val actor = system.actorOf(Props(new BitcoinPeerActor(peerGroup, blockchainActor.props,
      _ => walletActor.props, keyPairs = Seq.empty, blockchain, network, connectionRetryInterval)))
    walletActor.expectCreation()
    blockchainActor.expectCreation()
  }
}
