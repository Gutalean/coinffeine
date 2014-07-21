package coinffeine.protocol.gateway.proto

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit.TestProbe
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import coinffeine.common.test.{AkkaSpec, DefaultTcpPortAllocator}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.serialization._

class ProtoMessageGatewayTest
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("MessageGatewaySystem"))
  with Eventually with IntegrationPatience {

  val timeout = 10.seconds
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

  it must "report connect failures" in new FreshBrokerAndPeer {
    val ref = system.actorOf(messageGatewayProps)
    ref ! Connect(
      localPort = DefaultTcpPortAllocator.allocatePort(),
      connectTo = BrokerAddress("localhost", DefaultTcpPortAllocator.allocatePort()))
    expectMsgType[ConnectingError](5 seconds)
  }

  trait FreshBrokerAndPeer extends ProtoMessageGateway.Component
      with TestProtocolSerializationComponent with CoinffeineUnitTestNetwork.Component {
    val brokerAddress = BrokerAddress("localhost", DefaultTcpPortAllocator.allocatePort())
    val (brokerGateway, brokerProbe, brokerId) = createBrokerGateway(localPort = brokerAddress.port)
    val (peerGateway, peerProbe) = createPeerGateway(brokerAddress)

    def createPeerGateway(connectTo: BrokerAddress): (ActorRef, TestProbe) = {
      val localPort = DefaultTcpPortAllocator.allocatePort()
      val ref = system.actorOf(messageGatewayProps)
      ref ! Connect(localPort, connectTo)
      val Connected(_, _) = expectMsgType[Connected](5 seconds)
      val probe = TestProbe()
      probe.send(ref, Subscribe(_ => true))
      (ref, probe)
    }

    def createBrokerGateway(localPort: Int): (ActorRef, TestProbe, PeerId) = {
      val ref = system.actorOf(messageGatewayProps)
      ref ! Bind(localPort)
      val Bound(brokerId) = expectMsgType[Bound](5 seconds)
      val probe = TestProbe()
      probe.send(ref, Subscribe(_ => true))
      (ref, probe, brokerId)
    }

    // Send an initial message to the broker gateway to make it know its PeerConnection
    peerGateway ! ForwardMessage(randomOrderMatch(), brokerId)
    private val msg = brokerProbe.expectMsgType[ReceiveMessage[OrderMatch]]
    val peerId = msg.sender
  }
}
