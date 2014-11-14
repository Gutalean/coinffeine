package coinffeine.protocol.gateway.proto

import java.net.NetworkInterface
import scala.collection.JavaConversions._
import scala.concurrent.duration._

import akka.actor._
import akka.pattern._

import coinffeine.common.akka.ServiceActor
import coinffeine.model.network._
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.p2p.P2PNetwork
import coinffeine.protocol.gateway.p2p.P2PNetwork.{PortForwardedPeerNode, AutodetectPeerNode, StandaloneNode}
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

private class ProtobufServerActor(properties: MutableCoinffeineNetworkProperties,
                                  ignoredNetworkInterfaces: Seq[NetworkInterface],
                                  p2pNetwork: P2PNetwork,
                                  connectionRetryInterval: FiniteDuration)
  extends Actor with ServiceActor[Join] with ActorLogging {

  import context.dispatcher
  import ProtobufServerActor._

  private val acceptedNetworkInterfaces = NetworkInterface.getNetworkInterfaces
    .filterNot(ignoredNetworkInterfaces.contains)

  private var session: Option[P2PNetwork.Session] = None
  private var connections: Map[PeerId, ActorRef] = Map.empty

  private object ConnectionListener extends P2PNetwork.Listener {
    override def peerCountUpdated(peers: Int): Unit = {
      self ! PeerCountUpdated(peers)
    }

    override def messageReceived(peer: PeerId, payload: Array[Byte]): Unit = {
      self ! ReceiveData(peer, payload)
    }
  }

  override protected def starting(join: Join): Receive = {
    val listener = sender()
    connect(join)
    becomeStarted {
      case newSession: P2PNetwork.Session =>
        session = Some(newSession)
        new InitializedServer(newSession.brokerId, listener).becomeStarted()

      case Status.Failure(cause) =>
        log.error(cause, "Cannot connect as {}. Retrying in {}", join, connectionRetryInterval)
        context.system.scheduler.scheduleOnce(connectionRetryInterval, self, RetryConnection)

      case RetryConnection =>
        disconnect()
        connect(join)
    } orElse manageConnectionStatus
  }

  private def connect(join: Join): Unit = {
    val mode = join.role match {
      case BrokerNode => StandaloneNode(join.settings.brokerEndpoint)
      case PeerNode if join.settings.externalEndpoint.isDefined =>
        PortForwardedPeerNode(join.settings.externalEndpoint.get, join.settings.brokerEndpoint)
      case PeerNode => AutodetectPeerNode(join.settings.peerPort, join.settings.brokerEndpoint)
    }
    p2pNetwork.join(join.id, mode, acceptedNetworkInterfaces.toSeq, ConnectionListener).pipeTo(self)
  }

  override protected def stopping(): Receive = {
    log.info("Shutting down the protobuf server")
    disconnect()
    becomeStopped()
  }

  private def disconnect(): Unit = {
    connections.values.foreach(context.stop)
    connections = Map.empty
    session.foreach(_.close())
    session = None
  }

  private def updateConnectionStatus(activePeers: Int): Unit = {
    properties.activePeers.set(activePeers)
  }

  private def updateConnectionStatus(brokerId: Option[PeerId]): Unit = {
    properties.brokerId.set(brokerId)
  }

  private class InitializedServer(brokerId: PeerId, listener: ActorRef) {

    def started(): Receive = {
      updateConnectionStatus(Some(brokerId))
      handlingMessages orElse manageConnectionStatus
    }

    def becomeStarted(): Unit = {
      become(started())
    }

    private val handlingMessages: Receive = {
      case SendProtoMessage(to: PeerId, msg) => sendMessage(to, msg)
      case SendProtoMessage(BrokerId, msg) => sendMessage(brokerId, msg)
      case SendProtoMessageToBroker(msg) => sendMessage(brokerId, msg)
      case ReceiveData(from, data) =>
        val msg = CoinffeineMessage.parseFrom(data)
        val source = if (from == brokerId) BrokerId else from
        listener ! ReceiveProtoMessage(source, msg)
    }

    private def sendMessage(to: PeerId, msg: CoinffeineMessage): Unit = {
      connections.getOrElse(to, spawnConnectionActor(to)) ! ConnectionActor.Message(msg.toByteArray)
    }
  }

  private def spawnConnectionActor(to: PeerId): ActorRef = {
    val ref = context.actorOf(Props(new ConnectionActor(session.get, to)), to.value)
    connections += to -> ref
    ref
  }

  private def manageConnectionStatus: Receive = {
    case PeerCountUpdated(count) => updateConnectionStatus(count)
  }
}

private[gateway] object ProtobufServerActor {
  private case class PeerCountUpdated(count: Int)
  private case class ReceiveData(from: PeerId, data: Array[Byte])
  private case object RetryConnection

  def props(properties: MutableCoinffeineNetworkProperties,
            ignoredNetworkInterfaces: Seq[NetworkInterface],
            p2pNetwork: P2PNetwork,
            connectionRetryInterval: FiniteDuration): Props = Props(
    new ProtobufServerActor(properties, ignoredNetworkInterfaces, p2pNetwork, connectionRetryInterval))

  /** Send a message to a peer */
  case class SendProtoMessage(to: NodeId, msg: CoinffeineMessage)
  /** Send a message to the broker */
  case class SendProtoMessageToBroker(msg: CoinffeineMessage)

  /** Sent to the listener when a message is received */
  case class ReceiveProtoMessage(senderId: NodeId, msg: CoinffeineMessage)
}
