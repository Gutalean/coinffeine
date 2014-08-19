package coinffeine.protocol.gateway.proto

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit.TestProbe
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.{DefaultTcpPortAllocator, IgnoredNetworkInterfaces}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.event.{CoinffeineConnectionStatus, EventChannelProbe}
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.serialization._

class ProtoMessageGatewayTest
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("MessageGatewaySystem"))
  with Eventually with IntegrationPatience {

  val connectionTimeout = 30.seconds
  val subscribeToOrderMatches = MessageGateway.Subscribe {
    case ReceiveMessage(msg: OrderMatch, _) => true
    case _ => false
  }

  "Protobuf RPC Message gateway" must "send a known message to a remote peer" in
    new FreshBrokerAndPeer {
      val msg = randomOrderMatch()
      peerGateway ! ForwardMessage(msg, brokerId)
      brokerProbe.expectMsg(ReceiveMessage(msg, peerId))
    }

  it must "send a known message twice reusing the connection to the remote peer" in
    new FreshBrokerAndPeer {
      val (msg1, msg2) = (randomOrderMatch(), randomOrderMatch())
      peerGateway ! ForwardMessage(msg1, brokerId)
      peerGateway ! ForwardMessage(msg2, brokerId)
      brokerProbe.expectMsgAllOf(ReceiveMessage(msg1, peerId), ReceiveMessage(msg2, peerId))
    }

  it must "deliver messages to subscribers when filter match" in new FreshBrokerAndPeer {
    val msg = randomOrderMatch()
    peerGateway ! subscribeToOrderMatches
    brokerGateway ! ForwardMessage(msg, peerId)
    expectMsg(ReceiveMessage(msg, brokerId))
  }

  it must "do not deliver messages to subscribers when filter doesn't match" in
    new FreshBrokerAndPeer {
      peerGateway ! Subscribe(_ => false)
      brokerGateway ! ForwardMessage(randomOrderMatch(), peerId)
      expectNoMsg()
    }

  it must "deliver messages to several subscribers when filter match" in new FreshBrokerAndPeer {
    val msg = randomOrderMatch()
    val subs = for (i <- 1 to 5) yield TestProbe()
    subs.foreach(_.send(peerGateway, subscribeToOrderMatches))
    brokerGateway ! ForwardMessage(msg, peerId)
    subs.foreach(_.expectMsg(ReceiveMessage(msg, brokerId)))
  }

  it must "report join failures" in new Fixture {
    val ref = createMessageGateway()
    ref ! Join(
      localPort = DefaultTcpPortAllocator.allocatePort(),
      connectTo = BrokerAddress("localhost", DefaultTcpPortAllocator.allocatePort()))
    expectMsgType[JoinError](30 seconds)
  }

  it must "retrieve the connection status when disconnected" in new Fixture {
    val ref = createMessageGateway()
    ref ! MessageGateway.RetrieveConnectionStatus
    expectMsg(CoinffeineConnectionStatus(activePeers = 0, brokerId = None))
  }

  it must "retrieve the connection status when connected" in new FreshBrokerAndPeer {
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
      case other =>
        println(s"FISHING: $other")
        false
    }
  }

  trait Fixture extends ProtoMessageGateway.Component
      with TestProtocolSerializationComponent
      with CoinffeineUnitTestNetwork.Component
      with IgnoredNetworkInterfaces {

    def createMessageGateway(): ActorRef = system.actorOf(messageGatewayProps(ignoredNetworkInterfaces))

    def createPeerGateway(connectTo: BrokerAddress): (ActorRef, TestProbe) = {
      val localPort = DefaultTcpPortAllocator.allocatePort()
      val ref = createMessageGateway()
      ref ! Join(localPort, connectTo)
      val Joined(_, _) = expectMsgType[Joined](connectionTimeout)
      val probe = TestProbe()
      probe.send(ref, Subscribe(_ => true))
      (ref, probe)
    }

    def createBrokerGateway(localPort: Int): (ActorRef, TestProbe, PeerId) = {
      val ref = createMessageGateway()
      ref ! Bind(localPort)
      val Bound(_, brokerId) = expectMsgType[Bound](connectionTimeout)
      val probe = TestProbe()
      probe.send(ref, Subscribe(_ => true))
      (ref, probe, brokerId)
    }
  }

  trait FreshBrokerAndPeer extends Fixture {
    val eventChannelProbe = EventChannelProbe()
    val brokerAddress = BrokerAddress("localhost", DefaultTcpPortAllocator.allocatePort())
    val (brokerGateway, brokerProbe, brokerId) = createBrokerGateway(localPort = brokerAddress.port)
    val (peerGateway, peerProbe) = createPeerGateway(brokerAddress)

    // Send an initial message to the broker gateway to make it know its PeerConnection
    peerGateway ! ForwardMessage(randomOrderMatch(), brokerId)
    private val msg = brokerProbe.expectMsgType[ReceiveMessage[OrderMatch]]
    val peerId = msg.sender
  }
}
