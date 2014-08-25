package coinffeine.protocol.gateway.proto

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit.TestProbe

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.{DefaultTcpPortAllocator, IgnoredNetworkInterfaces}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.event.{CoinffeineConnectionStatus, EventChannelProbe}
import coinffeine.model.market.OrderId
import coinffeine.model.network.{BrokerId, PeerId}
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.serialization._

class ProtoMessageGatewayTest
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("MessageGatewaySystem"))
  with ProtoServerAssertions {

  val subscribeToOrderMatches = MessageGateway.Subscribe {
    case ReceiveMessage(_: OrderMatch, _) =>
  }

  "Protobuf RPC Message gateway" must "send a known message to a remote peer" in
    new FreshBrokerAndPeer {
      val msg = randomOrderMatch()
      peerGateway ! ForwardMessage(msg, BrokerId)
      brokerProbe.expectMsg(ReceiveMessage(msg, peerId))
    }

  it must "send a known message twice reusing the connection to the remote peer" in
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

  it must "retrieve the connection status" in new FreshBrokerAndPeer {
    peerGateway ! MessageGateway.RetrieveConnectionStatus
    expectMsg(CoinffeineConnectionStatus(activePeers = 1, Some(brokerId)))
  }

  it must "produce connection status events" in new FreshBrokerAndPeer {
    eventChannelProbe.fishForMessage(hint = "notify disconnected status") {
      case CoinffeineConnectionStatus(0, None) => true
      case _ => false
    }
    eventChannelProbe.fishForMessage(hint = "notify connected status") {
      case CoinffeineConnectionStatus(1, Some(`brokerId`)) => true
      case other => false
    }
  }

  it must "subscribe to broker messages" in new FreshBrokerAndPeer {
    val probe = TestProbe()
    probe.send(peerGateway, Subscribe.fromBroker {
      case _: OrderMatch =>
    })
    val message = randomOrderMatch()
    brokerGateway ! ForwardMessage(message, peerId)
    probe.expectMsg(ReceiveMessage(message, BrokerId))
  }

  it must "forward messages to the broker" in new FreshBrokerAndPeer {
    val msg = randomOrderMatch()
    peerGateway ! ForwardMessageToBroker(msg)
    brokerProbe.expectMsg(ReceiveMessage(msg, peerId))
  }

  it must "stop successfully" in new FreshBrokerAndPeer {
    peerGateway ! ServiceActor.Stop
    expectMsg(ServiceActor.Stopped)
  }

  trait Fixture extends ProtoMessageGateway.Component
      with TestProtocolSerializationComponent
      with CoinffeineUnitTestNetwork.Component
      with IgnoredNetworkInterfaces {

    private val subscribeToAnything = Subscribe { case _ => }

    def createMessageGateway(): ActorRef = system.actorOf(messageGatewayProps(ignoredNetworkInterfaces))

    def createPeerGateway(connectTo: BrokerAddress): (ActorRef, TestProbe) = {
      val localPort = DefaultTcpPortAllocator.allocatePort()
      val ref = createMessageGateway()
      ref ! ServiceActor.Start(JoinAsPeer(localPort, connectTo))
      expectMsg(ServiceActor.Started)
      waitForConnections(ref, minConnections = 1)
      val probe = TestProbe()
      probe.send(ref, subscribeToAnything)
      (ref, probe)
    }

    def createBrokerGateway(localPort: Int): (ActorRef, TestProbe, PeerId) = {
      val ref = createMessageGateway()
      ref ! ServiceActor.Start(JoinAsBroker(localPort))
      expectMsg(ServiceActor.Started)
      val brokerId = waitForConnections(ref, minConnections = 0)
      val probe = TestProbe()
      probe.send(ref, subscribeToAnything)
      (ref, probe, brokerId)
    }
  }

  trait FreshBrokerAndPeer extends Fixture {
    val eventChannelProbe = EventChannelProbe()
    val brokerAddress = BrokerAddress("localhost", DefaultTcpPortAllocator.allocatePort())
    val (brokerGateway, brokerProbe, brokerId) = createBrokerGateway(localPort = brokerAddress.port)
    val (peerGateway, peerProbe) = createPeerGateway(brokerAddress)

    // Send an initial message to the broker gateway to make it know its PeerConnection
    peerGateway ! ForwardMessage(randomOrderMatch(), BrokerId)
    private val msg = brokerProbe.expectMsgType[ReceiveMessage[OrderMatch]]
    val peerId = msg.sender
  }
}
