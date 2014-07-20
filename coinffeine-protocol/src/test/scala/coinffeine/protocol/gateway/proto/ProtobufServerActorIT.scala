package coinffeine.protocol.gateway.proto

import akka.actor.ActorRef
import org.scalatest.concurrent.{IntegrationPatience, Eventually}

import coinffeine.common.test.{DefaultTcpPortAllocator, AkkaSpec}
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway.{BrokerAddress, Bound, Bind}
import coinffeine.protocol.gateway.proto.ProtoMessageGateway.ReceiveProtoMessage
import coinffeine.protocol.gateway.proto.ProtobufServerActor.SendMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.{ProtocolVersion, Payload, CoinffeineMessage}

class ProtobufServerActorIT extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("ServerSystem"))
  with Eventually with IntegrationPatience {

  val msg = CoinffeineMessage.newBuilder()
    .setPayload(Payload.getDefaultInstance)
    .setVersion(ProtocolVersion.newBuilder().setMajor(1).setMinor(4))
    .build()

  "A peer" should "be able to send a message to another peer just with its peerId" in {
    val brokerPort = DefaultTcpPortAllocator.allocatePort()
    val (broker, brokerId) = createServer(brokerPort, connectTo = None)
    val brokerAddress = Some(BrokerAddress("localhost", brokerPort))

    val (peer1, receivedBrokerId1) = createServer(DefaultTcpPortAllocator.allocatePort(), brokerAddress)
    receivedBrokerId1 should be (brokerId)
    peer1 ! SendMessage(brokerId, msg)
    val peerId1 = expectMsgType[ReceiveProtoMessage].senderId

    val (peer2, receivedBrokerId2) = createServer(DefaultTcpPortAllocator.allocatePort(), brokerAddress)
    receivedBrokerId2 should be (brokerId)
    peer2 ! SendMessage(brokerId, msg)
    val peerId2 = expectMsgType[ReceiveProtoMessage].senderId

    peer1 ! SendMessage(peerId2, msg)
    expectMsg(ReceiveProtoMessage(msg, peerId1))
  }

  private def createServer(port: Int, connectTo: Option[BrokerAddress]): (ActorRef, PeerId) = {
    val peer = system.actorOf(ProtobufServerActor.props)
    peer ! Bind(port, connectTo)
    val Bound(brokerId) = expectMsgType[Bound]
    (peer, brokerId)
  }
}
