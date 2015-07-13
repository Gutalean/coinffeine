package coinffeine.protocol.gateway.overlay

import java.io.IOException
import scala.util.control.NoStackTrace

import akka.actor.{ActorRef, ActorSystem}
import akka.util.ByteString

import coinffeine.common.akka.test.{MockActor, MockSupervisedActor}
import coinffeine.model.network.NodeId
import coinffeine.overlay.{OverlayId, OverlayNetwork}
import coinffeine.protocol.Version
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage.MessageType
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}
import coinffeine.protocol.serialization.{CoinffeineMessage, Payload, ProtocolSerialization}

class MockOverlayNetwork(protocolSerialization: ProtocolSerialization)
                        (implicit system: ActorSystem) extends OverlayNetwork with IdConversions {

  private val mockClient = new MockSupervisedActor()
  private var listener = ActorRef.noSender
  private var overlayId: OverlayId = _
  private val error = new IOException("Injected error") with NoStackTrace
  override val clientProps = mockClient.props()

  def expectClientSpawn(): Unit = {
    mockClient.expectCreation()
  }

  def expectJoinAs(id: OverlayId): Unit = {
    listener = mockClient.probe.expectMsgPF() {
      case MockActor.MockReceived(_, sender, OverlayNetwork.Join(`id`)) => sender
    }
    overlayId = id
  }

  def acceptJoin(networkSize: Int): Unit = {
    mockClient.probe.send(listener,
      OverlayNetwork.Joined(overlayId, OverlayNetwork.NetworkStatus(networkSize)))
  }

  def rejectJoin(): Unit = {
    val cause = OverlayNetwork.UnderlyingNetworkFailure(error)
    mockClient.probe.send(listener, OverlayNetwork.JoinFailed(overlayId, cause))
  }

  def expectLeave(): Unit = {
    listener = mockClient.probe.expectMsgPF() {
      case MockActor.MockReceived(_, sender, OverlayNetwork.Leave) => sender
    }
  }

  def acceptLeave(): Unit = {
    mockClient.probe.send(listener, OverlayNetwork.Leaved(overlayId, OverlayNetwork.RequestedLeave))
  }

  def givenRandomDisconnection(): Unit = {
    val cause = OverlayNetwork.UnexpectedLeave(error)
    mockClient.probe.send(listener, OverlayNetwork.Leaved(overlayId, cause))
  }

  def receiveInvalidMessageFrom(sender: NodeId): Unit = {
    receiveFrom(sender, ByteString("Total nonsense"))
  }

  def receiveMessageOfProtocolVersion(version: Version, sender: NodeId): Unit = {
    val protobuf = proto.CoinffeineMessage.newBuilder()
      .setType(MessageType.PAYLOAD)
      .setPayload(proto.Payload.newBuilder().setVersion(
        proto.ProtocolVersion.newBuilder().setMajor(version.major).setMinor(version.minor)))
      .build()
    receiveFrom(sender, ByteString(protobuf.toByteArray))
  }

  def receiveFrom(sender: NodeId, message: PublicMessage): Unit = {
    receiveFrom(sender, Payload(message))
  }

  def receiveFrom(sender: NodeId, message: CoinffeineMessage): Unit = {
    receiveFrom(sender, serialize(message))
  }

  private def receiveFrom(sender: NodeId, bytes: ByteString): Unit = {
    val receive = OverlayNetwork.ReceiveMessage(sender.toOverlayId, bytes)
    mockClient.probe.send(listener, receive)
  }

  def expectSendTo(target: NodeId, message: PublicMessage): Unit = {
    expectSendTo(target, Payload(message))
  }

  def expectSendTo(target: NodeId, message: CoinffeineMessage): Unit = {
    mockClient.expectMsg(OverlayNetwork.SendMessage(target.toOverlayId, serialize(message)))
  }

  private def serialize(message: CoinffeineMessage): ByteString =
    protocolSerialization.serialize(message)
      .getOrElse(throw new UnsupportedOperationException("cannot serialize"))
}
