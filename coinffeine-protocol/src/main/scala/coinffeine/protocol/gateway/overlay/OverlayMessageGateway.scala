package coinffeine.protocol.gateway.overlay

import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.util.ByteString

import coinffeine.common.akka.ServiceActor
import coinffeine.model.network._
import coinffeine.overlay.OverlayNetwork
import coinffeine.overlay.relay.client.{ClientConfig, RelayNetwork}
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.gateway.{SubscriptionManagerActor, MessageGateway}
import coinffeine.protocol.gateway.MessageGateway.{Subscribe, Unsubscribe}
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage
import coinffeine.protocol.serialization.{ProtocolSerializationComponent, ProtocolSerialization}

/** Message gateway that uses an overlay network as transport */
private class OverlayMessageGateway(
    settings: MessageGatewaySettings,
    overlay: OverlayNetwork,
    serialization: ProtocolSerialization,
    properties: MutableCoinffeineNetworkProperties)
  extends Actor with ServiceActor[Unit] with ActorLogging with IdConversions {

  import context.dispatcher

  private val subscriptions = context.actorOf(SubscriptionManagerActor.props)
  private var executionOpt: Option[ServiceExecution] = None

  override def preStart(): Unit = {
    properties.activePeers.set(0)
    properties.brokerId.set(Some(PeerId("f" * 40)))
  }

  override protected def starting(args: Unit): Receive = {
    val newExecution = new ServiceExecution()
    executionOpt = Some(newExecution)
    newExecution.start()
  }

  override protected def stopped = delegateSubscriptionManagement

  private class ServiceExecution {
    val overlayId = settings.peerId.toOverlayId
    val client = context.actorOf(overlay.clientProps)

    def start(): Receive = {
      client ! OverlayNetwork.Join(overlayId)
      becomeStarted(joining)
    }

    def stop(): Receive = {
      client ! OverlayNetwork.Leave
      handle {
        case OverlayNetwork.Leaved(_, _) => becomeStopped()
      }
    }

    private def joining: Receive =
      countingActivePeers orElse delegateSubscriptionManagement orElse receivingJoinResult

    private def joined: Receive = countingActivePeers orElse delegateSubscriptionManagement orElse {
      case leave: OverlayNetwork.Leaved =>
        scheduleReconnection(s"Unexpected disconnection: ${leave.cause}")

      case MessageGateway.ForwardMessage(message, dest) =>
        serializeMessage(message) match  {
          case Success(payload) =>
            client ! OverlayNetwork.SendMessage(dest.toOverlayId, payload)
          case Failure(ex) =>
            log.error(ex, "Cannot serialize message {}", message)
        }

      case OverlayNetwork.CannotSend(request, cause) =>
        log.error("Cannot send message to {}: cause", request.target, cause)
    }

    private def waitingToRejoin = delegateSubscriptionManagement orElse rejoining

    private def receivingJoinResult: Receive = {
      case OverlayNetwork.JoinFailed(_, cause) =>
        scheduleReconnection(s"Cannot join as ${settings.peerId}: $cause")

      case OverlayNetwork.Joined(_) =>
        log.info("Joined as {}", settings.peerId)
        become(joined)
    }

    private def rejoining: Receive = {
      case OverlayMessageGateway.Rejoin =>
        client ! OverlayNetwork.Join(overlayId)
        become(joining)
    }

    private def scheduleReconnection(cause: String): Unit = {
      val delay = settings.connectionRetryInterval
      log.error("{}. Next join attempt in {}", cause, delay)
      context.system.scheduler.scheduleOnce(delay, self, OverlayMessageGateway.Rejoin)
      context.become(waitingToRejoin)
    }
  }

  override protected def stopping(): Receive = {
    executionOpt.fold(becomeStopped()) { execution =>
      executionOpt = None
      execution.stop()
    }
  }

  override protected def becomeStopped() = {
    properties.activePeers.set(0)
    super.becomeStopped()
  }

  private def delegateSubscriptionManagement: Receive = {
    case msg @ Subscribe(_) =>
      context.watch(sender())
      subscriptions forward msg

    case msg @ Unsubscribe => subscriptions forward msg

    case Terminated(actor) => subscriptions.tell(Unsubscribe, actor)

    case OverlayNetwork.ReceiveMessage(source, bytes) =>
      deserializeMessage(bytes) match {
        case Success(message) =>
          val receive = MessageGateway.ReceiveMessage(message, source.toNodeId)
          subscriptions ! SubscriptionManagerActor.NotifySubscribers(receive)
        case Failure(ex) =>
          log.error(ex, "Dropping invalid incoming message from {}", source.toNodeId)
      }
  }

  private def countingActivePeers: Receive = {
    case OverlayNetwork.NetworkStatus(networkSize) =>
      log.debug("Network size {}", networkSize)
      properties.activePeers.set(networkSize - 1)
  }

  private def serializeMessage(message: PublicMessage): Try[ByteString] = Try {
    ByteString(serialization.toProtobuf(message).toByteArray)
  }

  private def deserializeMessage(bytes: ByteString): Try[PublicMessage] = Try {
    val protobuf = CoinffeineMessage.parseFrom(bytes.toArray)
    serialization.fromProtobuf(protobuf)
  }
}

object OverlayMessageGateway {
  private case object Rejoin

  trait Component extends MessageGateway.Component {
    this: ProtocolSerializationComponent with MutableCoinffeineNetworkProperties.Component =>

    override def messageGatewayProps(settings: MessageGatewaySettings)(system: ActorSystem) = {
      val overlay = new RelayNetwork(ClientConfig(
        host = settings.brokerEndpoint.hostname,
        port = settings.brokerEndpoint.port
      ), system)
      Props(new OverlayMessageGateway(settings, overlay, protocolSerialization,
        coinffeineNetworkProperties))
    }
  }
}
