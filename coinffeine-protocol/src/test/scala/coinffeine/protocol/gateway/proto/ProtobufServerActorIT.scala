package coinffeine.protocol.gateway.proto

import akka.actor.ActorRef

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.{DefaultTcpPortAllocator, IgnoredNetworkInterfaces}
import coinffeine.model.network.{MutableCoinffeineNetworkProperties, PeerId}
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.proto.ProtobufServerActor.{ReceiveProtoMessage, SendProtoMessage}
import coinffeine.protocol.protobuf.CoinffeineProtobuf.{CoinffeineMessage, Payload, ProtocolVersion}

class ProtobufServerActorIT extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("ServerSystem"))
  with ProtoServerAssertions with IgnoredNetworkInterfaces {

  private val msg = CoinffeineMessage.newBuilder()
    .setPayload(Payload.getDefaultInstance)
    .setVersion(ProtocolVersion.newBuilder().setMajor(1).setMinor(4))
    .build()

  "A peer" should "be able to send a message to another peer just with its peerId" in {
    val brokerPort = DefaultTcpPortAllocator.allocatePort()
    val (_, brokerId) = createBroker(brokerPort)
    val brokerAddress = BrokerAddress("localhost", brokerPort)

    val (peer1, receivedBrokerId1) = createPeer(DefaultTcpPortAllocator.allocatePort(), brokerAddress)
    receivedBrokerId1 should be (brokerId)
    peer1 ! SendProtoMessage(brokerId, msg)
    val peerId1 = expectMsgType[ReceiveProtoMessage].senderId

    val (peer2, receivedBrokerId2) = createPeer(DefaultTcpPortAllocator.allocatePort(), brokerAddress)
    receivedBrokerId2 should be (brokerId)
    peer2 ! SendProtoMessage(brokerId, msg)
    val peerId2 = expectMsgType[ReceiveProtoMessage].senderId

    peer1 ! SendProtoMessage(peerId2, msg)
    expectMsg(ReceiveProtoMessage(peerId1, msg))
  }

  private def createBroker(port: Int): (ActorRef, PeerId) = {
    val properties = new MutableCoinffeineNetworkProperties
    val peer = system.actorOf(
      ProtobufServerActor.props(properties, ignoredNetworkInterfaces), s"broker-$port")
    peer ! ServiceActor.Start(JoinAsBroker(PeerId.random(), port))
    expectMsg(ServiceActor.Started)
    val brokerId = waitForConnections(properties, minConnections = 0)
    (peer, brokerId)
  }

  private def createPeer(port: Int, connectTo: BrokerAddress): (ActorRef, PeerId) = {
    val properties = new MutableCoinffeineNetworkProperties
    val peer = system.actorOf(
      ProtobufServerActor.props(properties, ignoredNetworkInterfaces), s"peer-$port")
    peer ! ServiceActor.Start(JoinAsPeer(PeerId.random(), port, connectTo))
    expectMsg(ServiceActor.Started)
    val brokerId = waitForConnections(properties, minConnections = 1)
    (peer, brokerId)
  }
}
