package coinffeine.peer.bitcoin

import akka.actor.Props
import akka.testkit.TestProbe
import com.google.bitcoin.core.{FullPrunedBlockChain, PeerGroup}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore
import org.scalatest.mock.MockitoSugar

import coinffeine.common.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.Wallet
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork

class BitcoinPeerActorTest extends AkkaSpec with MockitoSugar {

  "The bitcoin peer actor" should "connect to the bitcoin network and create delegates" in
    new Fixture {
      actor ! BitcoinPeerActor.Start(eventChannel)
      blockchainActor.expectCreation()
      blockchainActor.expectMsg(BlockchainActor.Initialize(blockchain))
      walletActor.expectCreation()
      walletActor.expectMsgType[WalletActor.Initialize]
      expectMsg(BitcoinPeerActor.Started(walletActor.ref))
    }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    val peerGroup = new PeerGroup(network)

    val blockchainActor, walletActor = new MockSupervisedActor()
    val eventChannel = TestProbe().ref
    val wallet = new Wallet(network)
    val blockchain = new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, 1000))
    val actor = system.actorOf(Props(
      new BitcoinPeerActor(peerGroup, blockchainActor.props, walletActor.props, wallet, blockchain)))
  }
}
