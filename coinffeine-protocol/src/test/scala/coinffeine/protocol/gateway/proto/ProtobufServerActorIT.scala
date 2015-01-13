package coinffeine.protocol.gateway.proto

import java.net.InetAddress
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit._

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.{DefaultTcpPortAllocator, IgnoredNetworkInterfaces}
import coinffeine.model.network.{NetworkEndpoint, MutableCoinffeineNetworkProperties, PeerId}
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.MessageGatewayAssertions
import coinffeine.protocol.gateway.p2p.TomP2PNetwork
import coinffeine.protocol.gateway.proto.ProtobufServerActor.{ReceiveProtoMessage, SendProtoMessage}
import coinffeine.protocol.protobuf.CoinffeineProtobuf.{CoinffeineMessage, Payload, ProtocolVersion}

class ProtobufServerActorIT extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("ServerSystem"))
  with MessageGatewayAssertions with IgnoredNetworkInterfaces {

  private val localhost = InetAddress.getLocalHost.getCanonicalHostName
  private val connectionRetryInterval = 3.seconds.dilated
  private val msg = CoinffeineMessage.newBuilder()
    .setPayload(Payload.getDefaultInstance)
    .setVersion(ProtocolVersion.newBuilder().setMajor(1).setMinor(4))
    .build()

  "A peer" should "be able to send a message to another peer just with its peerId" in {
    val brokerPort = DefaultTcpPortAllocator.allocatePort()
    val (_, brokerId) = createBroker(brokerPort)
    val brokerAddress = NetworkEndpoint(localhost, brokerPort)

    val (peer1, receivedBrokerId1) = createPeer(DefaultTcpPortAllocator.allocatePort(), brokerAddress)
    receivedBrokerId1 shouldBe brokerId
    peer1 ! SendProtoMessage(brokerId, msg)
    val peerId1 = expectMsgType[ReceiveProtoMessage].senderId

    val (peer2, receivedBrokerId2) = createPeer(DefaultTcpPortAllocator.allocatePort(), brokerAddress)
    receivedBrokerId2 shouldBe brokerId
    peer2 ! SendProtoMessage(brokerId, msg)
    val peerId2 = expectMsgType[ReceiveProtoMessage].senderId

    peer1 ! SendProtoMessage(peerId2, msg)
    expectMsg(ReceiveProtoMessage(peerId1, msg))
  }

  private def createBroker(port: Int): (ActorRef, PeerId) = {
    val properties = new MutableCoinffeineNetworkProperties
    val peer = system.actorOf(
      ProtobufServerActor.props(properties, ignoredNetworkInterfaces, TomP2PNetwork, connectionRetryInterval),
      s"broker-$port"
    )
    val settings = MessageGatewaySettings(
      peerId = PeerId.random(),
      peerPort = 0,
      brokerEndpoint = NetworkEndpoint(localhost, port),
      ignoredNetworkInterfaces,
      connectionRetryInterval,
      externalForwardedPort = None
    )
    peer ! ServiceActor.Start(Join(BrokerNode, settings))
    expectMsg(ServiceActor.Started)
    val brokerId = waitForConnections(properties, minConnections = 0)
    (peer, brokerId)
  }

  private def createPeer(port: Int, connectTo: NetworkEndpoint): (ActorRef, PeerId) = {
    val properties = new MutableCoinffeineNetworkProperties
    val peer = system.actorOf(
      ProtobufServerActor.props(properties, ignoredNetworkInterfaces, TomP2PNetwork, connectionRetryInterval),
      s"peer-$port"
    )
    val settings = MessageGatewaySettings(
      peerId = PeerId.random(),
      peerPort = port,
      brokerEndpoint = connectTo,
      ignoredNetworkInterfaces,
      connectionRetryInterval,
      externalForwardedPort = None
    )
    peer ! ServiceActor.Start(Join(PeerNode, settings))
    expectMsg(ServiceActor.Started)
    val brokerId = waitForConnections(properties, minConnections = 1)
    (peer, brokerId)
  }
}
