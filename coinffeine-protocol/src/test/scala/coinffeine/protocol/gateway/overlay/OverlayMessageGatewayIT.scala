package coinffeine.protocol.gateway.overlay

import scala.concurrent.duration._

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.{ShouldMatchers, fixture}

import coinffeine.common.akka.Service
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.network._
import coinffeine.model.order.OrderId
import coinffeine.overlay.test.FakeOverlayNetwork
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.properties.DefaultCoinffeineNetworkProperties
import coinffeine.protocol.serialization.test.TestProtocolSerializationComponent

class OverlayMessageGatewayIT extends fixture.FlatSpec with ShouldMatchers with Eventually {

  private val subscribeToOrderMatches = MessageGateway.Subscribe {
    case ReceiveMessage(_: OrderMatch, _) =>
  }

  "Overlay message gateway" must "send a message to a remote peer" in { f =>
    val msg = f.randomOrderMatch()
    f.peerProbe.send(f.peerGateway, ForwardMessage(msg, BrokerId))
    f.brokerProbe.expectMsg(ReceiveMessage(msg, f.peerId))
  }

  it must "send a message twice reusing the connection to the remote peer" in { f =>
    val (msg1, msg2) = (f.randomOrderMatch(), f.randomOrderMatch())
    f.peerProbe.send(f.peerGateway, ForwardMessage(msg1, BrokerId))
    f.peerGateway ! ForwardMessage(msg2, BrokerId)
    f.brokerProbe.expectMsgAllOf(ReceiveMessage(msg1, f.peerId), ReceiveMessage(msg2, f.peerId))
  }

  it must "deliver messages to subscribers when filter match" in { f =>
    val msg = f.randomOrderMatch()
    f.peerProbe.send(f.peerGateway, subscribeToOrderMatches)
    f.brokerProbe.send(f.brokerGateway, ForwardMessage(msg, f.peerId))
    f.peerProbe.expectMsg(ReceiveMessage(msg, BrokerId))
  }

  it must "support multiple subscriptions from the same actor" in { f =>
    val msg1 = f.randomOrderMatch().copy(orderId = OrderId("1"))
    val msg2 = msg1.copy(orderId = OrderId("2"))
    val probe = f.createPeerProbe()
    probe.send(f.peerGateway, Subscribe {
      case ReceiveMessage(orderMatch: OrderMatch, _) if orderMatch.orderId == OrderId("1") =>
    })
    probe.send(f.peerGateway, Subscribe {
      case ReceiveMessage(orderMatch: OrderMatch, _) if orderMatch.orderId == OrderId("2") =>
    })
    f.brokerProbe.send(f.brokerGateway, ForwardMessage(msg1, f.peerId))
    probe.expectMsg(ReceiveMessage(msg1, BrokerId))
    f.brokerProbe.send(f.brokerGateway, ForwardMessage(msg2, f.peerId))
    probe.expectMsg(ReceiveMessage(msg2, BrokerId))
  }

  it must "do not deliver messages to subscribers when filter doesn't match" in { f =>
    val probe = f.createPeerProbe()
    probe.send(f.peerGateway, Subscribe(Map.empty))
    f.brokerProbe.send(f.brokerGateway, ForwardMessage(f.randomOrderMatch(), f.peerId))
    probe.expectNoMsg(100.millis)
  }

  it must "deliver messages to several subscribers when filter match" in { f =>
    val msg = f.randomOrderMatch()
    val subs = Seq.fill(5)(f.createPeerProbe())
    subs.foreach(_.send(f.peerGateway, subscribeToOrderMatches))
    f.brokerProbe.send(f.brokerGateway, ForwardMessage(msg, f.peerId))
    subs.foreach(_.expectMsg(ReceiveMessage(msg, BrokerId)))
  }

  it must "set the connection status properties" in { f =>
    eventually {
      f.peerNetworkProperties.activePeers.get shouldBe 1
      f.peerNetworkProperties.brokerId.get shouldBe 'defined
    }
  }

  it must "subscribe to broker messages" in { f =>
    f.peerProbe.send(f.peerGateway, Subscribe.fromBroker {
      case _: OrderMatch =>
    })
    val message = f.randomOrderMatch()
    f.brokerProbe.send(f.brokerGateway, ForwardMessage(message, f.peerId))
    f.peerProbe.expectMsg(ReceiveMessage(message, BrokerId))
  }

  it must "forward messages to the broker" in { f =>
    val msg = f.randomOrderMatch()
    f.peerProbe.send(f.peerGateway, ForwardMessage(msg, BrokerId))
    f.brokerProbe.expectMsg(ReceiveMessage(msg, f.peerId))
  }

  it must "stop successfully" in { f =>
    f.peerProbe.send(f.peerGateway, Service.Stop)
    f.peerProbe.expectMsg(Service.Stopped)
  }

  override type FixtureParam = Fixture

  override protected def withFixture(test: OneArgTest) = {
    val fixture = new Fixture
    try {
      test(fixture)
    } finally {
      fixture.shutdown()
    }
  }

  class Fixture extends TestProtocolSerializationComponent
      with CoinffeineUnitTestNetwork.Component {

    val brokerTestKit = new TestKit(ActorSystem("broker"))
    val peerTestKit = new TestKit(ActorSystem("peer"))

    private val connectionTimeout = 5.seconds.dilated(brokerTestKit.system)
    private val subscribeToAnything = Subscribe { case _ => }
    private val overlay = FakeOverlayNetwork(connectionFailureRate = 0.1)(brokerTestKit.system)

    val peerNetworkProperties = new DefaultCoinffeineNetworkProperties()(peerTestKit.system)
    val brokerNetworkProperties = new DefaultCoinffeineNetworkProperties()(brokerTestKit.system)
    val connectionRetryInterval = 100.millis.dilated(brokerTestKit.system)

    def createMessageGateway(settings: MessageGatewaySettings, system: ActorSystem): ActorRef =
      system.actorOf(Props(
        new OverlayMessageGateway(settings, overlay, protocolSerialization)))

    def createPeerGateway() =
      createGateway(PeerId.random(), peerTestKit.system, peerNetworkProperties, minConnections = 1)
    def createBrokerGateway() =
      createGateway(PeerId("f" * 40), brokerTestKit.system, brokerNetworkProperties, minConnections = 0)

    def createGateway(peerId: PeerId,
                      system: ActorSystem,
                      networkProperties: DefaultCoinffeineNetworkProperties,
                      minConnections: Int): (ActorRef, TestProbe) = {
      val probe = TestProbe()(system)
      val ref = createMessageGateway(MessageGatewaySettings(peerId, connectionRetryInterval), system)
      probe.send(ref, Service.Start {})
      probe.expectMsg(Service.Started)
      waitForConnections(networkProperties, minConnections)
      probe.send(ref, subscribeToAnything)
      (ref, probe)
    }

    def waitForConnections(properties: CoinffeineNetworkProperties, minConnections: Int): Unit = {
      eventually(timeout = Timeout(connectionTimeout)) {
        properties.activePeers.get should be >= minConnections
        properties.brokerId.get shouldBe 'defined
      }
    }

    def createPeerProbe(): TestProbe = {
      TestProbe()(peerTestKit.system)
    }

    def shutdown(): Unit = {
      brokerTestKit.shutdown()
      peerTestKit.shutdown()
    }

    val (brokerGateway, brokerProbe) = createBrokerGateway()
    val (peerGateway, peerProbe) = createPeerGateway()

    // Send an initial message to the broker gateway to make it know its PeerConnection
    peerGateway ! ForwardMessage(randomOrderMatch(), BrokerId)
    private val msg = brokerProbe.expectMsgType[ReceiveMessage[OrderMatch]]
    val peerId = msg.sender
  }

}
