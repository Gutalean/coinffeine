package coinffeine.protocol.gateway.overlay

import scala.concurrent.duration._

import akka.actor.{ActorRef, Props}
import akka.testkit._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import coinffeine.common.akka.Service
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.market.OrderId
import coinffeine.model.network._
import coinffeine.overlay.test.FakeOverlayNetwork
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.serialization._

class OverlayMessageGatewayIT
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("MessageGatewaySystem")) with Eventually {

  private val connectionTimeout = 5.seconds.dilated
  private val subscribeToOrderMatches = MessageGateway.Subscribe {
    case ReceiveMessage(_: OrderMatch[_], _) =>
  }

  "Overlay message gateway" must "send a message to a remote peer" in
    new FreshBrokerAndPeer {
      val msg = randomOrderMatch()
      peerGateway ! ForwardMessage(msg, BrokerId)
      brokerProbe.expectMsg(ReceiveMessage(msg, peerId))
    }

  it must "send a message twice reusing the connection to the remote peer" in
    new FreshBrokerAndPeer {
      val (msg1, msg2) = (randomOrderMatch(), randomOrderMatch())
      peerGateway ! ForwardMessage(msg1, BrokerId)
      peerGateway ! ForwardMessage(msg2, BrokerId)
      brokerProbe.expectMsgAllOf(ReceiveMessage(msg1, peerId), ReceiveMessage(msg2, peerId))
    }

  it must "deliver messages to subscribers when filter match" in new FreshBrokerAndPeer {
    val msg = randomOrderMatch()
    peerGateway ! subscribeToOrderMatches
    brokerGateway ! ForwardMessage(msg, peerId)
    expectMsg(ReceiveMessage(msg, BrokerId))
  }

  it must "support multiple subscriptions from the same actor" in new FreshBrokerAndPeer {
    val msg1 = randomOrderMatch().copy(orderId = OrderId("1"))
    val msg2 = msg1.copy(orderId = OrderId("2"))
    peerGateway ! Subscribe {
      case ReceiveMessage(OrderMatch(OrderId("1"), _, _, _, _, _), _) =>
    }
    peerGateway ! Subscribe {
      case ReceiveMessage(OrderMatch(OrderId("2"), _, _, _, _, _), _) =>
    }
    brokerGateway ! ForwardMessage(msg1, peerId)
    expectMsg(ReceiveMessage(msg1, BrokerId))
    brokerGateway ! ForwardMessage(msg2, peerId)
    expectMsg(ReceiveMessage(msg2, BrokerId))
  }

  it must "do not deliver messages to subscribers when filter doesn't match" in
    new FreshBrokerAndPeer {
      peerGateway ! Subscribe(Map.empty)
      brokerGateway ! ForwardMessage(randomOrderMatch(), peerId)
      expectNoMsg(100.millis)
    }

  it must "deliver messages to several subscribers when filter match" in new FreshBrokerAndPeer {
    val msg = randomOrderMatch()
    val subs = for (i <- 1 to 5) yield TestProbe()
    subs.foreach(_.send(peerGateway, subscribeToOrderMatches))
    brokerGateway ! ForwardMessage(msg, peerId)
    subs.foreach(_.expectMsg(ReceiveMessage(msg, BrokerId)))
  }

  it must "set the connection status properties" in new FreshBrokerAndPeer {
    eventually {
      peerNetworkProperties.activePeers.get shouldBe 1
      peerNetworkProperties.brokerId.get shouldBe 'defined
    }
  }

  it must "subscribe to broker messages" in new FreshBrokerAndPeer {
    val probe = TestProbe()
    probe.send(peerGateway, Subscribe.fromBroker {
      case _: OrderMatch[_] =>
    })
    val message = randomOrderMatch()
    brokerGateway ! ForwardMessage(message, peerId)
    probe.expectMsg(ReceiveMessage(message, BrokerId))
  }

  it must "forward messages to the broker" in new FreshBrokerAndPeer {
    val msg = randomOrderMatch()
    peerGateway ! ForwardMessage(msg, BrokerId)
    brokerProbe.expectMsg(ReceiveMessage(msg, peerId))
  }

  it must "stop successfully" in new FreshBrokerAndPeer {
    peerGateway ! Service.Stop
    expectMsg(Service.Stopped)
  }

  trait Fixture extends TestProtocolSerializationComponent
      with CoinffeineUnitTestNetwork.Component {

    private val subscribeToAnything = Subscribe { case _ => }
    private val overlay = FakeOverlayNetwork(connectionFailureRate = 0.1)

    val peerNetworkProperties = new MutableCoinffeineNetworkProperties
    val brokerNetworkProperties = new MutableCoinffeineNetworkProperties
    val connectionRetryInterval = 100.millis.dilated

    def createMessageGateway(networkProperties: MutableCoinffeineNetworkProperties,
                             settings: MessageGatewaySettings): ActorRef =
      system.actorOf(Props(
        new OverlayMessageGateway(settings, overlay, protocolSerialization, networkProperties)))

    def createPeerGateway() =
      createGateway(PeerId.random(), peerNetworkProperties, minConnections = 1)
    def createBrokerGateway() =
      createGateway(PeerId("f" * 40), brokerNetworkProperties, minConnections = 0)

    def createGateway(peerId: PeerId,
                      networkProperties: MutableCoinffeineNetworkProperties,
                      minConnections: Int): (ActorRef, TestProbe) = {
      val ref = createMessageGateway(
        networkProperties, MessageGatewaySettings(peerId, connectionRetryInterval))
      ref ! Service.Start {}
      expectMsg(Service.Started)
      waitForConnections(networkProperties, minConnections)
      val probe = TestProbe()
      probe.send(ref, subscribeToAnything)
      (ref, probe)
    }

    def waitForConnections(properties: CoinffeineNetworkProperties, minConnections: Int): Unit = {
      eventually(timeout = Timeout(connectionTimeout)) {
        properties.activePeers.get should be >= minConnections
        properties.brokerId.get shouldBe 'defined
      }
    }
  }

  trait FreshBrokerAndPeer extends Fixture {
    val (brokerGateway, brokerProbe) = createBrokerGateway()
    val (peerGateway, peerProbe) = createPeerGateway()

    // Send an initial message to the broker gateway to make it know its PeerConnection
    peerGateway ! ForwardMessage(randomOrderMatch(), BrokerId)
    private val msg = brokerProbe.expectMsgType[ReceiveMessage[OrderMatch[_]]]
    val peerId = msg.sender
  }
}
