package coinffeine.peer.bitcoin

import akka.actor.Props
import akka.testkit.TestProbe
import com.google.bitcoin.core.{FullPrunedBlockChain, PeerGroup}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.event.BitcoinConnectionStatus.NotDownloading
import coinffeine.model.event.{BitcoinConnectionStatus, EventChannelProbe}

class BitcoinPeerActorTest extends AkkaSpec with MockitoSugar {

  "The bitcoin peer actor" should "connect to the bitcoin network and create delegates" in
    new Fixture {
      actor ! BitcoinPeerActor.Start
      blockchainActor.expectCreation()
      blockchainActor.expectMsg(BlockchainActor.Initialize(blockchain))
      walletActor.expectCreation()
      expectMsg(BitcoinPeerActor.Started)
    }

  it should "report connection status" in new Fixture {
    actor ! BitcoinPeerActor.Start
    walletActor.expectCreation()
    expectMsg(BitcoinPeerActor.Started)
    eventChannelProbe.expectMsgClass(classOf[BitcoinConnectionStatus])
  }

  it should "retrieve connection status on demand" in new Fixture {
    actor ! BitcoinPeerActor.Start
    actor ! BitcoinPeerActor.RetrieveConnectionStatus
    expectMsg(BitcoinConnectionStatus(activePeers = 0, NotDownloading))
  }

  it should "retrieve the blockchain actor" in new Fixture {
    blockchainActor.expectCreation()
    val probe = TestProbe()
    probe.send(actor, BitcoinPeerActor.RetrieveBlockchainActor)
    probe.expectMsg(BitcoinPeerActor.BlockchainActorRef(blockchainActor.ref))
  }

  it should "retrieve the wallet actor" in new Fixture {
    walletActor.expectCreation()
    val probe = TestProbe()
    probe.send(actor, BitcoinPeerActor.RetrieveWalletActor)
    probe.expectMsg(BitcoinPeerActor.WalletActorRef(walletActor.ref))
  }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    val peerGroup = new PeerGroup(network)
    val blockchainActor, walletActor = new MockSupervisedActor()
    val eventChannelProbe = EventChannelProbe()
    val blockchain = new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, 1000))
    val actor = system.actorOf(Props(new BitcoinPeerActor(peerGroup, blockchainActor.props,
      _ => walletActor.props, keyPairs = Seq.empty, blockchain, network)))
  }
}
