package coinffeine.protocol.gateway.overlay

import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.util.ByteString

import coinffeine.common.akka.ServiceActor
import coinffeine.model.network._
import coinffeine.overlay.OverlayNetwork
import coinffeine.overlay.relay.client.{ClientConfig, RelayNetwork}
import coinffeine.protocol.gateway.{SubscriptionManagerActor, MessageGateway}
import coinffeine.protocol.gateway.MessageGateway.{Join, Subscribe, Unsubscribe}
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage
import coinffeine.protocol.serialization.{ProtocolSerializationComponent, ProtocolSerialization}

/** Message gateway that uses an overlay network as transport */
private class OverlayMessageGateway(
    overlay: OverlayMessageGateway.OverlayNetworkAdapter[_ <: OverlayNetwork],
    serialization: ProtocolSerialization,
    properties: MutableCoinffeineNetworkProperties)
  extends Actor with ServiceActor[Join] with ActorLogging with IdConversions {

  import context.dispatcher

  private val subscriptions = context.actorOf(SubscriptionManagerActor.props)
  private var executionOpt: Option[ServiceExecution] = None

  override def preStart(): Unit = {
    properties.activePeers.set(0)
    properties.brokerId.set(Some(PeerId("f" * 40)))
  }

  override protected def starting(join: Join): Receive = {
    val newExecution = new ServiceExecution(join)
    executionOpt = Some(newExecution)
    newExecution.start()
  }

  override protected def stopped = delegateSubscriptionManagement

  private class ServiceExecution(join: Join) {
    val overlayId = join.id.toOverlayId
    val client = context.actorOf(overlay.clientProps(join))

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
        scheduleReconnection(s"Cannot join as ${join.id}: $cause")

      case OverlayNetwork.Joined(_) =>
        log.info("Joined as {}", join.id)
        become(joined)
    }

    private def rejoining: Receive = {
      case OverlayMessageGateway.Rejoin =>
        client ! OverlayNetwork.Join(overlayId)
        become(joining)
    }

    private def scheduleReconnection(cause: String): Unit = {
      val delay = join.settings.connectionRetryInterval
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

  abstract class OverlayNetworkAdapter[O <: OverlayNetwork](val overlay: O) {
    def config(join: MessageGateway.Join): overlay.Config
    def clientProps(join: MessageGateway.Join): Props = overlay.clientProps(config(join))
  }

  class RelayNetworkAdapter(system: ActorSystem)
    extends OverlayNetworkAdapter[RelayNetwork](overlay = new RelayNetwork(system)) {
    override def config(join: Join): overlay.Config = ClientConfig(
      host = join.settings.brokerEndpoint.hostname,
      port = join.settings.brokerEndpoint.port
    )
  }

  trait Component extends MessageGateway.Component {
    this: ProtocolSerializationComponent with MutableCoinffeineNetworkProperties.Component =>

    override def messageGatewayProps(system: ActorSystem) = Props(new OverlayMessageGateway(
      new RelayNetworkAdapter(system), protocolSerialization, coinffeineNetworkProperties))
  }
}
