package coinffeine.protocol.gateway.proto

import java.net.NetworkInterface
import scala.concurrent.duration._

import akka.actor._
import akka.util.Timeout

import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway._
import coinffeine.protocol.gateway.proto.ProtoMessageGateway.ReceiveProtoMessage
import coinffeine.protocol.gateway.proto.ProtobufServerActor.SendMessage
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}
import coinffeine.protocol.serialization.{ProtocolSerialization, ProtocolSerializationComponent}

private class ProtoMessageGateway(serialization: ProtocolSerialization,
                                  ignoredNetworkInterfaces: Seq[NetworkInterface])
  extends Actor with ActorLogging {

  implicit private val timeout = Timeout(10.seconds)

  private val subscriptions = context.actorOf(SubscriptionManagerActor.props, "subscriptions")
  private val server = context.actorOf(
    ProtobufServerActor.props(ignoredNetworkInterfaces), "server")

  override def receive = waitingForInitialization orElse managingSubscriptionsAndConnectionStatus

  private val managingSubscriptionsAndConnectionStatus: Receive = {
    case msg @ (Subscribe(_) | SubscribeToBroker(_)) =>
      context.watch(sender())
      subscriptions forward msg
    case msg @ Unsubscribe => subscriptions forward msg
    case Terminated(actor) => subscriptions.tell(Unsubscribe, actor)
    case msg @ RetrieveConnectionStatus => server forward msg
  }

  private val waitingForInitialization: Receive = {
    case msg @ (Bind(_) | Join(_, _)) =>
      server ! msg
      context.become(starting(sender()) orElse managingSubscriptionsAndConnectionStatus)
  }

  private def starting(listener: ActorRef): Receive = {
    case response @ Bound(port, peerId) =>
      subscriptions ! SubscriptionManagerActor.ConnectedToBroker(peerId)
      listener ! response
      log.info(s"Message gateway successfully bounded on port {} as {}", port, peerId)
      context.become(forwardingMessages(peerId) orElse managingSubscriptionsAndConnectionStatus)

    case response @ Joined(myId, brokerId) =>
      subscriptions ! SubscriptionManagerActor.ConnectedToBroker(brokerId)
      listener ! response
      log.info(s"Message gateway successfully joined to network as {} using broker {}",
        myId, brokerId)
      context.become(forwardingMessages(brokerId) orElse managingSubscriptionsAndConnectionStatus)

    case error @ BindingError(port, cause) =>
      log.error(cause, "Message gateway failed to bind on port {}", port)
      listener ! error
      context.become(receive)

    case error @ JoinError(cause) =>
      log.error(cause, "Message gateway failed to join to the network")
      listener ! error
      context.become(receive)
  }

  private def forwardingMessages(brokerId: PeerId): Receive = {
    case ForwardMessage(msg, destId) =>
      forward(destId, msg)

    case ForwardMessageToBroker(msg) =>
      forward(brokerId, msg)

    case ReceiveProtoMessage(protoMessage, senderId) =>
      val message = serialization.fromProtobuf(protoMessage)
      subscriptions ! ReceiveMessage(message, senderId)
  }

  private def forward(to: PeerId, message: PublicMessage): Unit = {
    log.debug("Forwarding message {} to {}", message, to)
    server ! SendMessage(to, serialization.toProtobuf(message))
  }
}

object ProtoMessageGateway {

  case class ReceiveProtoMessage(message: proto.CoinffeineMessage, senderId: PeerId)

  trait Component extends MessageGateway.Component {
    this: ProtocolSerializationComponent with NetworkComponent=>

    override def messageGatewayProps(ignoredNetworkInterfaces: Seq[NetworkInterface]) = Props(
      new ProtoMessageGateway(protocolSerialization, ignoredNetworkInterfaces))
  }
}
