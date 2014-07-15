package coinffeine.protocol.gateway

import coinffeine.protocol.gateway.protorpc.ProtoRpcMessageGateway

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit.{EventFilter, TestProbe}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.serialization._
import com.coinffeine.common.test.{AkkaSpec, DefaultTcpPortAllocator}

class ProtoRpcMessageGatewayTest
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("MessageGatewaySystem"))
  with Eventually with IntegrationPatience {

  val timeout = 10.seconds
  val subscribeToOrderMatches = MessageGateway.Subscribe {
    case ReceiveMessage(msg: OrderMatch, _) => true
    case _ => false
  }

  "Protobuf RPC Message gateway" must "send a known message to a remote peer" in
    new FreshPairOfGateways {
      val msg = randomOrderMatch()
      peerGateway ! ForwardMessage(msg, brokerId)
      brokerProbe.expectMsg(ReceiveMessage(msg, peerId))
    }

  it must "send a known message twice reusing the connection to the remote peer" in
    new FreshPairOfGateways {
      val (msg1, msg2) = (randomOrderMatch(), randomOrderMatch())
      peerGateway ! ForwardMessage(msg1, brokerId)
      peerGateway ! ForwardMessage(msg2, brokerId)
      brokerProbe.expectMsgAllOf(ReceiveMessage(msg1, peerId), ReceiveMessage(msg2, peerId))
    }

  it must "throw while forwarding when recipient was never connected" in new FreshGateway {
    val msg = randomOrderMatch()
    EventFilter[Throwable](occurrences = 1) intercept {
      peerGateway ! ForwardMessage(msg, brokerId)
    }
  }

  it must "deliver messages to subscribers when filter match" in new FreshPairOfGateways {
    val msg = randomOrderMatch()
    peerGateway ! subscribeToOrderMatches
    brokerGateway ! ForwardMessage(msg, peerId)
    expectMsg(ReceiveMessage(msg, brokerId))
  }

  it must "do not deliver messages to subscribers when filter doesn't match" in
    new FreshPairOfGateways {
      peerGateway ! Subscribe(_ => false)
      brokerGateway ! ForwardMessage(randomOrderMatch(), peerId)
      expectNoMsg()
    }

  it must "deliver messages to several subscribers when filter match" in new FreshPairOfGateways {
    val msg = randomOrderMatch()
    val subs = for (i <- 1 to 5) yield TestProbe()
    subs.foreach(_.send(peerGateway, subscribeToOrderMatches))
    brokerGateway ! ForwardMessage(msg, peerId)
    subs.foreach(_.expectMsg(ReceiveMessage(msg, brokerId)))
  }

  trait FreshGateway extends ProtoRpcMessageGateway.Component
      with TestProtocolSerializationComponent with CoinffeineUnitTestNetwork.Component {

    val brokerId = PeerId("broker")
    val brokerConnection = allocateLocalPeerConnection()
    val peerId = PeerId("peer")
    val peerConnection = allocateLocalPeerConnection()

    val (peerGateway, peerProbe) = createGateway(peerId, peerConnection)

    def createGateway(peerId: PeerId, peerConnection: PeerConnection): (ActorRef, TestProbe) = {
      val ref = system.actorOf(messageGatewayProps)
      eventually {
        ref ! Bind(peerId, peerConnection, brokerId, brokerConnection)
        expectMsg(BoundTo(peerConnection))
      }
      val probe = TestProbe()
      probe.send(ref, Subscribe(_ => true))
      (ref, probe)
    }

    private def allocateLocalPeerConnection() =
      PeerConnection("localhost", DefaultTcpPortAllocator.allocatePort())
  }

  trait FreshPairOfGateways extends FreshGateway {
    val (brokerGateway, brokerProbe) = createGateway(brokerId, brokerConnection)

    // Send an initial message to the broker gateway to make it know its PeerConnection
    peerGateway ! ForwardMessage(randomOrderMatch(), brokerId)
    brokerProbe.expectMsgClass(classOf[ReceiveMessage[OrderMatch]])
  }
}
