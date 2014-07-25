package coinffeine.peer.bitcoin

import akka.actor.Props
import akka.testkit.TestProbe
import com.google.bitcoin.core.PeerGroup
import org.scalatest.mock.MockitoSugar

import coinffeine.common.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork

class BitcoinPeerActorTest extends AkkaSpec with MockitoSugar {

  "The bitcoin peer actor" should "connect to the bitcoin network and create delegates" in
    new Fixture {
      actor ! BitcoinPeerActor.Start(eventChannel)
      expectMsg(BitcoinPeerActor.Started)
      blockchain.expectCreation()
    }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    val peerGroup = new PeerGroup(network)

    val blockchain = new MockSupervisedActor()
    val eventChannel = TestProbe().ref
    val actor = system.actorOf(Props(new BitcoinPeerActor(peerGroup, blockchain.props)))
  }
}
