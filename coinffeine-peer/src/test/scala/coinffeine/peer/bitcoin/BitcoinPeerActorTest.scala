package coinffeine.peer.bitcoin

import akka.actor.Props
import com.google.bitcoin.core.{FullPrunedBlockChain, PeerGroup}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.test.{MockSupervisedActor, AkkaSpec}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.peer.api.event.EventChannelProbe

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

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    val peerGroup = new PeerGroup(network)
    val blockchainActor, walletActor = new MockSupervisedActor()
    val eventChannelProbe = EventChannelProbe()
    val blockchain = new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, 1000))
    val actor = system.actorOf(Props(new BitcoinPeerActor(
      peerGroup, blockchainActor.props, walletActor.props, keyPairs = Seq.empty, blockchain, network)))
  }
}
