package coinffeine.protocol.gateway.proto

import java.net.{InetAddress, InetSocketAddress, NetworkInterface}
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor._
import akka.pattern._

import coinffeine.common.akka.ServiceActor
import coinffeine.model.network._
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.p2p.P2PNetwork
import coinffeine.protocol.gateway.p2p.P2PNetwork.{AutodetectPeerNode, StandaloneNode}
import coinffeine.protocol.gateway.p2p.TomP2PNetwork.Connection
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

  private var connection: Option[P2PNetwork.Session] = None

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
      case conn: Connection =>
        connection = Some(conn)
        new InitializedServer(conn.brokerId, listener).becomeStarted()

      case Status.Failure(cause) =>
        log.error(cause, "Cannot connect as {}. Retrying in {}", join, connectionRetryInterval)
        context.system.scheduler.scheduleOnce(connectionRetryInterval, self, RetryConnection)

      case RetryConnection =>
        disconnect()
        connect(join)
    } orElse manageConnectionStatus
  }

  private def connect(join: Join): Unit = (for {
    brokerAddress <- resolveBrokerAddress(join.brokerAddress)
    mode = join match {
      case JoinAsBroker(_, _) => StandaloneNode(brokerAddress)
      case JoinAsPeer(_, localPort, _) => AutodetectPeerNode(localPort, brokerAddress)
    }
    conn <- p2pNetwork.join(join.id, mode, acceptedNetworkInterfaces.toSeq, ConnectionListener)
  } yield conn).pipeTo(self)

  override protected def stopping(): Receive = {
    log.info("Shutting down the protobuf server")
    disconnect()
    becomeStopped()
  }

  private def disconnect(): Unit = {
    connection.foreach(_.close())
    connection = None
  }

  private def updateConnectionStatus(activePeers: Int): Unit = {
    properties.activePeers.set(activePeers)
  }

  private def updateConnectionStatus(brokerId: Option[PeerId]): Unit = {
    properties.brokerId.set(brokerId)
  }

  private def resolveBrokerAddress(broker: BrokerAddress): Future[InetSocketAddress] = Future {
    new InetSocketAddress(InetAddress.getByName(broker.hostname), broker.port)
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
      connection.get.connect(to).map(_.send(msg.toByteArray))
    }
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
