package coinffeine.protocol.gateway.overlay

import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.util.ByteString

import coinffeine.common.akka.ServiceActor
import coinffeine.model.network._
import coinffeine.overlay.OverlayNetwork
import coinffeine.overlay.OverlayNetwork.NetworkStatus
import coinffeine.overlay.relay.client.RelayNetwork
import coinffeine.overlay.relay.settings.RelayClientSettings
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.gateway.MessageGateway.{Subscribe, Unsubscribe}
import coinffeine.protocol.gateway.{MessageGateway, SubscriptionManagerActor}
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage
import coinffeine.protocol.serialization.{ProtocolSerialization, ProtocolSerializationComponent}

/** Message gateway that uses an overlay network as transport */
private class OverlayMessageGateway(
    settings: MessageGatewaySettings,
    overlay: OverlayNetwork,
    serialization: ProtocolSerialization,
    properties: MutableCoinffeineNetworkProperties)
  extends Actor with ServiceActor[Unit] with ActorLogging with IdConversions {

  import context.dispatcher

  private val subscriptions = context.actorOf(SubscriptionManagerActor.props)
  private var stoppingBehavior: () => Receive = super.stopping

  override def preStart(): Unit = {
    properties.activePeers.set(0)
    properties.brokerId.set(Some(PeerId("f" * 40)))
  }

  override protected def starting(args: Unit): Receive = {
    new ServiceExecution().start()
  }

  override protected def stopped = delegateSubscriptionManagement
  override protected def stopping(): Receive = stoppingBehavior()

  private class ServiceExecution {
    val overlayId = settings.peerId.toOverlayId
    val client = context.actorOf(overlay.clientProps)

    def start(): Receive = {
      client ! OverlayNetwork.Join(overlayId)
      stoppingBehavior = stopWhenJoining _
      becomeStarted(joining)
    }

    private def joining: Receive =
      countingActivePeers orElse delegateSubscriptionManagement orElse receivingJoinResult

    private def stopWhenJoining: Receive = {
      log.info("Waiting for the join result before stopping")
      handle {
        case OverlayNetwork.JoinFailed(_, cause) =>
          log.info(s"Cannot join as ${settings.peerId}: $cause. Not retrying.")
          becomeStopped()

        case _: OverlayNetwork.Joined =>
          log.info("Leaving the network before stopping")
          client ! OverlayNetwork.Leave

        case _: OverlayNetwork.Leaved => becomeStopped()
      }
    }

    private def joined: Receive = countingActivePeers orElse delegateSubscriptionManagement orElse {
      case leave: OverlayNetwork.Leaved =>
        properties.activePeers.set(0)
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

    private def stopWhenJoined: Receive = {
      log.info("Leaving the network")
      client ! OverlayNetwork.Leave
      handle {
        case OverlayNetwork.Leaved(_, _) => becomeStopped()
      }
    }

    private def waitingToRejoin = delegateSubscriptionManagement orElse rejoining

    private def receivingJoinResult: Receive = {
      case OverlayNetwork.JoinFailed(_, cause) =>
        scheduleReconnection(s"Cannot join as ${settings.peerId}: $cause")

      case OverlayNetwork.Joined(_, NetworkStatus(networkSize)) =>
        log.info("Joined as {} to a network of size {}", settings.peerId, networkSize)
        properties.activePeers.set(networkSize - 1)
        stoppingBehavior = stopWhenJoined _
        become(joined)
    }

    private def rejoining: Receive = {
      case OverlayMessageGateway.Rejoin =>
        client ! OverlayNetwork.Join(overlayId)
        stoppingBehavior = stopWhenJoining _
        become(joining)
    }

    private def scheduleReconnection(cause: String): Unit = {
      val delay = settings.connectionRetryInterval
      log.error("{}. Next join attempt in {}", cause, delay)
      context.system.scheduler.scheduleOnce(delay, self, OverlayMessageGateway.Rejoin)
      stoppingBehavior = OverlayMessageGateway.super.stopping
      become(waitingToRejoin)
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

    override def messageGatewayProps(messageGatewaySettings: MessageGatewaySettings,
                                     relayClientSettings: RelayClientSettings)
                                    (system: ActorSystem) =
      Props(new OverlayMessageGateway(
        messageGatewaySettings,
        new RelayNetwork(relayClientSettings, system),
        protocolSerialization,
        coinffeineNetworkProperties)
      )
  }
}
