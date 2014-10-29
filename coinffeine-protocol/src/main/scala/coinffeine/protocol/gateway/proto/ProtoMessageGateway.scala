package coinffeine.protocol.gateway.proto

import java.net.NetworkInterface

import akka.actor._

import coinffeine.common.akka.ServiceActor
import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.model.network.MutableCoinffeineNetworkProperties
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway._
import coinffeine.protocol.gateway.proto.ProtobufServerActor.{ReceiveProtoMessage, SendProtoMessage}
import coinffeine.protocol.gateway.proto.SubscriptionManagerActor.NotifySubscribers
import coinffeine.protocol.serialization.{ProtocolSerialization, ProtocolSerializationComponent}

private class ProtoMessageGateway(properties: MutableCoinffeineNetworkProperties,
                                  serialization: ProtocolSerialization,
                                  ignoredNetworkInterfaces: Seq[NetworkInterface])
  extends Actor with ServiceActor[Join] with ActorLogging {

  private val subscriptions = context.actorOf(SubscriptionManagerActor.props, "subscriptions")
  private val server = context.actorOf(
    ProtobufServerActor.props(properties, ignoredNetworkInterfaces), "server")

  override protected def starting(join: Join): Receive = {
    server ! ServiceActor.Start(join)
    handle {
      case ServiceActor.Started =>
        becomeStarted(started)

      case ServiceActor.StartFailure(cause) =>
        cancelStart(cause)
    }
  }

  private def started: Receive = {
    case msg @ Subscribe(_) =>
      context.watch(sender())
      subscriptions forward msg

    case msg @ Unsubscribe => subscriptions forward msg

    case Terminated(actor) => subscriptions.tell(Unsubscribe, actor)

    case ForwardMessage(msg, to) =>
      log.debug("Forwarding message {} to {}", msg, to)
      server ! SendProtoMessage(to, serialization.toProtobuf(msg))

    case ReceiveProtoMessage(senderId, protoMessage) =>
      try {
        val message = serialization.fromProtobuf(protoMessage)
        subscriptions ! NotifySubscribers(ReceiveMessage(message, senderId))
      } catch {
        case e: ProtocolSerialization.ProtocolVersionException =>
          log.error(e, "A message is received with an unexpected protocol version; dropping")
      }
  }

  override protected def stopping(): Receive = {
    server ! ServiceActor.Stop
    handle {
      case ServiceActor.Stopped => becomeStopped()
      case ServiceActor.StopFailure(cause) => cancelStop(cause)
    }
  }
}

object ProtoMessageGateway {

  trait Component extends MessageGateway.Component {
    this: ProtocolSerializationComponent with NetworkComponent
      with MutableCoinffeineNetworkProperties.Component =>

    override def messageGatewayProps(ignoredNetworkInterfaces: Seq[NetworkInterface]) = Props(
      new ProtoMessageGateway(
        coinffeineNetworkProperties, protocolSerialization, ignoredNetworkInterfaces))
  }
}
