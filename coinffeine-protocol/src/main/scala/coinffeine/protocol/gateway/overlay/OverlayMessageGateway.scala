package coinffeine.protocol.gateway.overlay

import akka.actor._

import coinffeine.alarms.akka.EventStreamReporting
import coinffeine.common.akka.ServiceLifecycle
import coinffeine.common.akka.event.CoinffeineEventProducer
import coinffeine.model.network._
import coinffeine.overlay.OverlayNetwork
import coinffeine.overlay.OverlayNetwork.NetworkStatus
import coinffeine.overlay.relay.client.RelayNetwork
import coinffeine.overlay.relay.settings.RelayClientSettings
import coinffeine.protocol.events.{ActiveCoinffeinePeersChanged, BrokerIdChanged}
import coinffeine.protocol.gateway.MessageGateway.{Subscribe, Unsubscribe}
import coinffeine.protocol.gateway.{MessageGateway, SubscriptionManagerActor}
import coinffeine.protocol.serialization.ProtocolSerialization.{DeserializationError, IncompatibleVersion}
import coinffeine.protocol.serialization._
import coinffeine.protocol.{MessageGatewaySettings, Version}

/** Message gateway that uses an overlay network as transport */
private class OverlayMessageGateway(
    settings: MessageGatewaySettings,
    overlay: OverlayNetwork,
    serialization: ProtocolSerialization) extends Actor with ServiceLifecycle[Unit]
  with EventStreamReporting with ActorLogging with IdConversions with CoinffeineEventProducer {

  import context.dispatcher

  private val subscriptions = context.actorOf(SubscriptionManagerActor.props)

  override def preStart(): Unit = {
    publish(ActiveCoinffeinePeersChanged(0))
    publish(BrokerIdChanged(PeerId("f" * 40)))
  }

  override protected def onStart(args: Unit) = new ServiceExecution().start()

  override protected def stopped = delegateSubscriptionManagement

  private class ServiceExecution {
    val overlayId = settings.peerId.toOverlayId
    val client = context.actorOf(overlay.clientProps)

    def start() = {
      client ! OverlayNetwork.Join(overlayId)
      BecomeStarted(joining, stopWhenJoining _)
    }

    private def joining: Receive =
      countingActivePeers orElse delegateSubscriptionManagement orElse receivingJoinResult

    private def stopWhenJoining = {
      log.info("Waiting for the join result before stopping")
      BecomeStopping {
        case OverlayNetwork.JoinFailed(_, cause) =>
          log.info(s"Cannot join as ${settings.peerId}: $cause. Not retrying.")
          completeStop()

        case _: OverlayNetwork.Joined =>
          log.info("Leaving the network before stopping")
          client ! OverlayNetwork.Leave

        case _: OverlayNetwork.Leaved => completeStop()
      }
    }

    private def joined: Receive = countingActivePeers orElse delegateSubscriptionManagement orElse {
      case leave: OverlayNetwork.Leaved =>
        publish(ActiveCoinffeinePeersChanged(0))
        scheduleReconnection(s"Unexpected disconnection: ${leave.cause}")

      case MessageGateway.ForwardMessage(message, dest) =>
        serialization.serialize(Payload(message)).fold(
          error => log.error("Cannot serialize message {}: {}", message, error),
          bytes => client ! OverlayNetwork.SendMessage(dest.toOverlayId, bytes)
        )

      case OverlayNetwork.CannotSend(request, cause) =>
        log.error("Cannot send message to {}: cause", request.target, cause)

      case OverlayNetwork.ReceiveMessage(source, bytes) =>
        val nodeId = source.toNodeId
        serialization.deserialize(bytes).fold(
          error => handleInvalidMessage(nodeId, error),
          message => handleMessage(nodeId, message)
        )
    }

    private def stopWhenJoined = {
      log.info("Leaving the network")
      client ! OverlayNetwork.Leave
      BecomeStopping {
        case OverlayNetwork.Leaved(_, _) =>
          publish(ActiveCoinffeinePeersChanged(0))
          completeStop()
      }
    }

    private def waitingToRejoin = delegateSubscriptionManagement orElse rejoining

    private def receivingJoinResult: Receive = {
      case OverlayNetwork.JoinFailed(_, cause) =>
        scheduleReconnection(s"Cannot join as ${settings.peerId}: $cause")

      case OverlayNetwork.Joined(_, NetworkStatus(networkSize)) =>
        log.info("Joined as {} to a network of size {}", settings.peerId, networkSize)
        publish(ActiveCoinffeinePeersChanged(networkSize - 1))
        become(joined, stopWhenJoined _)
    }

    private def rejoining: Receive = {
      case OverlayMessageGateway.Rejoin =>
        client ! OverlayNetwork.Join(overlayId)
        become(joining, stopWhenJoining _)
    }

    private def scheduleReconnection(cause: String): Unit = {
      val delay = settings.connectionRetryInterval
      log.error("{}. Next join attempt in {}", cause, delay)
      context.system.scheduler.scheduleOnce(delay, self, OverlayMessageGateway.Rejoin)
      become(waitingToRejoin, () => BecomeStopped) // TODO: use super?
    }

    private def handleMessage(source: NodeId, message: CoinffeineMessage): Unit = message match {
      case Payload(payload) =>
        val receive = MessageGateway.ReceiveMessage(payload, source)
        subscriptions ! SubscriptionManagerActor.NotifySubscribers(receive)

      case ProtocolMismatch(supportedVersion) =>
        alert(MessageGateway.ProtocolMismatchAlarm(Version.Current, supportedVersion))
    }

    private def handleInvalidMessage(source: NodeId, error: DeserializationError): Unit =
      error match {
        case IncompatibleVersion(actualVersion, expectedVersion) =>
          log.error("Message from {} of version {} when {} was expected. Notifying protocol mismatch.",
            source, actualVersion, expectedVersion)
          val bytes = serialization.serialize(ProtocolMismatch(expectedVersion)).toOption
          client ! OverlayNetwork.SendMessage(source.toOverlayId, bytes.get)
        case _ =>
          log.error("Dropping invalid incoming message from {}: {}", source, error)
      }
  }

  private def delegateSubscriptionManagement: Receive = {
    case msg @ Subscribe(_) =>
      context.watch(sender())
      subscriptions forward msg

    case msg @ Unsubscribe => subscriptions forward msg

    case Terminated(actor) => subscriptions.tell(Unsubscribe, actor)
  }

  private def countingActivePeers: Receive = {
    case OverlayNetwork.NetworkStatus(networkSize) =>
      log.debug("Network size {}", networkSize)
      publish(ActiveCoinffeinePeersChanged(networkSize - 1))
  }
}

object OverlayMessageGateway {
  private case object Rejoin

  trait Component extends MessageGateway.Component {
    this: ProtocolSerializationComponent =>

    override def messageGatewayProps(messageGatewaySettings: MessageGatewaySettings,
                                     relayClientSettings: RelayClientSettings)
                                    (system: ActorSystem) =
      Props(new OverlayMessageGateway(
        messageGatewaySettings,
        new RelayNetwork(relayClientSettings, system),
        protocolSerialization)
      )
  }
}
