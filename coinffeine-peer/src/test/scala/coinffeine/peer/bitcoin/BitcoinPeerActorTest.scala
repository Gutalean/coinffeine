package coinffeine.peer.bitcoin

import akka.actor.Props
import com.google.bitcoin.core.{FullPrunedBlockChain, PeerGroup}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.peer.api.event.BitcoinConnectionStatus.NotDownloading
import coinffeine.peer.api.event.{BitcoinConnectionStatus, EventChannelProbe}

class BitcoinPeerActorTest extends AkkaSpec with MockitoSugar {

  "The bitcoin peer actor" should "connect to the bitcoin network and create delegates" in
    new Fixture {
      actor ! BitcoinPeerActor.Start
      blockchainActor.expectCreation()
      blockchainActor.expectMsg(BlockchainActor.Initialize(blockchain))
      walletActor.expectCreation()
      walletActor.expectMsgType[WalletActor.Initialize]
      expectMsg(BitcoinPeerActor.Started(walletActor.ref))
    }

  it should "report connection status" in new Fixture {
    actor ! BitcoinPeerActor.Start
    walletActor.expectCreation()
    expectMsg(BitcoinPeerActor.Started(walletActor.ref))
    eventChannelProbe.expectMsgClass(classOf[BitcoinConnectionStatus])
  }

  it should "retrieve connection status on demand" in new Fixture {
    actor ! BitcoinPeerActor.Start
    actor ! BitcoinPeerActor.RetrieveConnectionStatus
    expectMsg(BitcoinConnectionStatus(activePeers = 0, NotDownloading))
  }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    val peerGroup = new PeerGroup(network)
    val blockchainActor, walletActor = new MockSupervisedActor()
    val eventChannelProbe = EventChannelProbe()
    val blockchain = new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, 1000))
    val actor = system.actorOf(Props(new BitcoinPeerActor(
      peerGroup, blockchainActor.props, walletActor.props, keyPairs = Seq.empty, blockchain, network)))
  }
}
