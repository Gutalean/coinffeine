package coinffeine.protocol.gateway.overlay

import java.io.IOException

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.ByteString

import coinffeine.common.akka.test.{MockActor, MockSupervisedActor}
import coinffeine.model.network.NodeId
import coinffeine.overlay.{OverlayId, OverlayNetwork}
import coinffeine.protocol.gateway.MessageGateway.Join
import coinffeine.protocol.gateway.overlay.OverlayMessageGateway.OverlayNetworkAdapter
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.serialization.TestProtocolSerialization

class MockOverlayNetwork(protocolSerialization: TestProtocolSerialization)
                        (implicit system: ActorSystem) extends OverlayNetwork with IdConversions {

  private val mockClient = new MockSupervisedActor()
  private var listener = ActorRef.noSender
  private var overlayId: OverlayId = _
  override type Config = Unit
  override def clientProps(config: Config): Props = mockClient.props()

  def adapter = new OverlayNetworkAdapter(this) {
    override def config(join: Join) = ()
  }

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
    mockClient.probe.send(listener, OverlayNetwork.Joined(overlayId))
    mockClient.probe.send(listener, OverlayNetwork.NetworkStatus(networkSize))
  }

  def rejectJoin(): Unit = {
    val cause = OverlayNetwork.UnderlyingNetworkFailure(new IOException("Injected error"))
    mockClient.probe.send(listener, OverlayNetwork.JoinFailed(overlayId, cause))
  }

  def givenRandomDisconnection(): Unit = {
    val cause = OverlayNetwork.UnexpectedLeave(new IOException("Injected error"))
    mockClient.probe.send(listener, OverlayNetwork.Leaved(overlayId, cause))
  }

  def receiveInvalidMessageFrom(sender: NodeId): Unit = {
    receiveFrom(sender, ByteString("Total nonsense"))
  }

  def receiveFrom(sender: NodeId, message: PublicMessage): Unit = {
    receiveFrom(sender, serialize(message))
  }

  private def receiveFrom(sender: NodeId, bytes: ByteString): Unit = {
    val receive = OverlayNetwork.ReceiveMessage(sender.toOverlayId, bytes)
    mockClient.probe.send(listener, receive)
  }

  def expectSendTo(target: NodeId, message: PublicMessage): Unit = {
    mockClient.expectMsg(OverlayNetwork.SendMessage(target.toOverlayId, serialize(message)))
  }

  private def serialize(message: PublicMessage): ByteString =
    ByteString(protocolSerialization.toProtobuf(message).toByteArray)
}
