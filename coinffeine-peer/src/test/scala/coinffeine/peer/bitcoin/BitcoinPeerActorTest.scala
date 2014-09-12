package coinffeine.peer.bitcoin

import scala.concurrent.duration._

import akka.actor.Props
import com.google.bitcoin.core.{Wallet, FullPrunedBlockChain, PeerGroup}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.MutableBitcoinProperties
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.event.EventChannelProbe

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

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    def connectionRetryInterval = 1.minute
    val peerGroup = new PeerGroup(network)
    val blockchainActor, walletActor = new MockSupervisedActor()
    val eventChannelProbe = EventChannelProbe()
    val wallet = new Wallet(network)
    val blockchain = new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, 1000))
    val properties = new MutableBitcoinProperties

    blockchain.addWallet(wallet)
    peerGroup.addWallet(wallet)

    val actor = system.actorOf(Props(new BitcoinPeerActor(properties,
      peerGroup, blockchainActor.props, (_, _) => walletActor.props, wallet,
      blockchain, network, connectionRetryInterval)))
    walletActor.expectCreation()
    blockchainActor.expectCreation()
  }
}
