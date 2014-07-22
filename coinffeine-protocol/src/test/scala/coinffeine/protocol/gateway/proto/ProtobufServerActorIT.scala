package coinffeine.protocol.gateway.proto

import akka.actor.ActorRef
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import coinffeine.common.test.{IgnoredNetworkInterfaces, AkkaSpec, DefaultTcpPortAllocator}
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.proto.ProtoMessageGateway.ReceiveProtoMessage
import coinffeine.protocol.gateway.proto.ProtobufServerActor.SendMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.{CoinffeineMessage, Payload, ProtocolVersion}

class ProtobufServerActorIT extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("ServerSystem"))
  with Eventually with IntegrationPatience with IgnoredNetworkInterfaces {

  val msg = CoinffeineMessage.newBuilder()
    .setPayload(Payload.getDefaultInstance)
    .setVersion(ProtocolVersion.newBuilder().setMajor(1).setMinor(4))
    .build()

  "A peer" should "be able to send a message to another peer just with its peerId" in {
    val brokerPort = DefaultTcpPortAllocator.allocatePort()
    val (broker, brokerId) = createBroker(brokerPort)
    val brokerAddress = BrokerAddress("localhost", brokerPort)

    val (peer1, receivedBrokerId1) = createPeer(DefaultTcpPortAllocator.allocatePort(), brokerAddress)
    receivedBrokerId1 should be (brokerId)
    peer1 ! SendMessage(brokerId, msg)
    val peerId1 = expectMsgType[ReceiveProtoMessage].senderId

    val (peer2, receivedBrokerId2) = createPeer(DefaultTcpPortAllocator.allocatePort(), brokerAddress)
    receivedBrokerId2 should be (brokerId)
    peer2 ! SendMessage(brokerId, msg)
    val peerId2 = expectMsgType[ReceiveProtoMessage].senderId

    peer1 ! SendMessage(peerId2, msg)
    expectMsg(ReceiveProtoMessage(msg, peerId1))
  }

  private def createBroker(port: Int): (ActorRef, PeerId) = {
    val peer = system.actorOf(ProtobufServerActor.props(ignoredNetworkInterfaces))
    peer ! Bind(port)
    val Bound(brokerId) = expectMsgType[Bound]
    (peer, brokerId)
  }

  private def createPeer(port: Int, connectTo: BrokerAddress): (ActorRef, PeerId) = {
    val peer = system.actorOf(ProtobufServerActor.props(ignoredNetworkInterfaces))
    peer ! Connect(port, connectTo)
    val Connected(_, brokerId) = expectMsgType[Connected]
    (peer, brokerId)
  }
}
