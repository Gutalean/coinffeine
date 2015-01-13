package coinffeine.protocol.gateway.overlay

import java.net.InetAddress
import scala.concurrent.duration._

import akka.actor.{ActorRef, Props}
import akka.testkit._

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.{DefaultTcpPortAllocator, IgnoredNetworkInterfaces}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.market.OrderId
import coinffeine.model.network.{BrokerId, MutableCoinffeineNetworkProperties, NetworkEndpoint, PeerId}
import coinffeine.overlay.test.FakeOverlayNetwork
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.overlay.OverlayMessageGateway.OverlayNetworkAdapter
import coinffeine.protocol.gateway.{MessageGateway, MessageGatewayAssertions}
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.serialization._

class OverlayMessageGatewayIT
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("MessageGatewaySystem"))
  with MessageGatewayAssertions {

  private val localhost = InetAddress.getLocalHost.getCanonicalHostName
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
      peerNetworkProperties.activePeers.get should be (1)
      peerNetworkProperties.brokerId.get should be (Some(brokerId))
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
    peerGateway ! ServiceActor.Stop
    expectMsg(ServiceActor.Stopped)
  }

  class FakeOverlayAdapter extends OverlayNetworkAdapter[FakeOverlayNetwork](
    FakeOverlayNetwork(connectionFailureRate = 0.1)) {
    override def config(join: Join) = FakeOverlayNetwork.Config
  }

  trait Fixture extends TestProtocolSerializationComponent
      with CoinffeineUnitTestNetwork.Component
      with IgnoredNetworkInterfaces {

    private val subscribeToAnything = Subscribe { case _ => }
    private val overlay = new FakeOverlayAdapter

    val peerNetworkProperties = new MutableCoinffeineNetworkProperties
    val brokerNetworkProperties = new MutableCoinffeineNetworkProperties
    val connectionRetryInterval = 100.millis.dilated

    def messageGatewayProps(networkProperties: MutableCoinffeineNetworkProperties) =
      Props(new OverlayMessageGateway(overlay, protocolSerialization, networkProperties))

    def createMessageGateway(networkProperties: MutableCoinffeineNetworkProperties): ActorRef =
      system.actorOf(messageGatewayProps(networkProperties))

    def createPeerGateway(connectTo: NetworkEndpoint): (ActorRef, TestProbe) = {
      val localPort = DefaultTcpPortAllocator.allocatePort()
      val ref = createMessageGateway(peerNetworkProperties)
      val settings = MessageGatewaySettings(
        peerId = PeerId.random(),
        peerPort = localPort,
        brokerEndpoint = connectTo,
        ignoredNetworkInterfaces,
        connectionRetryInterval,
        externalForwardedPort = None
      )
      ref ! ServiceActor.Start(Join(PeerNode, settings))
      expectMsg(ServiceActor.Started)
      waitForConnections(peerNetworkProperties, minConnections = 1)
      val probe = TestProbe()
      probe.send(ref, subscribeToAnything)
      (ref, probe)
    }

    def createBrokerGateway(localPort: Int): (ActorRef, TestProbe, PeerId) = {
      val ref = createMessageGateway(brokerNetworkProperties)
      val settings = MessageGatewaySettings(
        peerId = PeerId("f" * 40),
        peerPort = localPort,
        brokerEndpoint = NetworkEndpoint(localhost, localPort),
        ignoredNetworkInterfaces,
        connectionRetryInterval,
        externalForwardedPort = None
      )
      ref ! ServiceActor.Start(Join(BrokerNode, settings))
      expectMsg(ServiceActor.Started)
      val brokerId = waitForConnections(brokerNetworkProperties, minConnections = 0)
      val probe = TestProbe()
      probe.send(ref, subscribeToAnything)
      (ref, probe, brokerId)
    }
  }

  trait FreshBrokerAndPeer extends Fixture {
    val brokerAddress = NetworkEndpoint("localhost", DefaultTcpPortAllocator.allocatePort())
    val (brokerGateway, brokerProbe, brokerId) = createBrokerGateway(localPort = brokerAddress.port)
    val (peerGateway, peerProbe) = createPeerGateway(brokerAddress)

    // Send an initial message to the broker gateway to make it know its PeerConnection
    peerGateway ! ForwardMessage(randomOrderMatch(), BrokerId)
    private val msg = brokerProbe.expectMsgType[ReceiveMessage[OrderMatch[_]]]
    val peerId = msg.sender
  }
}
