package coinffeine.protocol.gateway.proto

import java.net.{InetSocketAddress, InetAddress, NetworkInterface}
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util._
import scala.util.control.NonFatal

import akka.actor._
import akka.pattern._
import net.tomp2p.connection.{Bindings, PeerConnection}
import net.tomp2p.futures.{FutureBootstrap, FutureDHT, FutureDiscover}
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.peers.{Number160, PeerAddress, PeerMapChangeListener}
import net.tomp2p.rpc.ObjectDataReply
import net.tomp2p.storage.Data

import coinffeine.common.akka.ServiceActor
import coinffeine.model.network._
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

private class ProtobufServerActor(properties: MutableCoinffeineNetworkProperties,
                                  ignoredNetworkInterfaces: Seq[NetworkInterface],
                                  connectionRetryInterval: FiniteDuration)
  extends Actor with ServiceActor[Join] with ActorLogging {

  import ProtobufServerActor._
  import context.dispatcher

  private val acceptedNetworkInterfaces = NetworkInterface.getNetworkInterfaces
    .filterNot(ignoredNetworkInterfaces.contains)

  private var me: Peer = _
  private var connections: Map[PeerAddress, PeerConnection] = Map.empty

  override protected def starting(join: Join): Receive = {
    val listener = sender()
    bindPeer(join.id, join.localPort, listener).pipeTo(self)
    becomeStarted {
      case peer: Peer =>
        me = peer
        become(join match {
          case JoinAsBroker(id, _) =>
            publishAddress().onComplete { case result =>
              self ! AddressPublicationResult(result)
            }
            publishingAddress(id, listener) orElse manageConnectionStatus
          case JoinAsPeer(id, _, broker) =>
            connectToBroker(id, broker, listener)
        })

      case Status.Failure(cause) =>
        log.error(cause, "Cannot bind at local port {}, retrying in {}",
          join.localPort, connectionRetryInterval)
        context.system.scheduler.scheduleOnce(connectionRetryInterval, self, RetryConnection)

      case RetryConnection =>
        bindPeer(join.id, join.localPort, listener).pipeTo(self)
    }
  }

  override protected def stopping(): Receive = {
    log.info("Shutting down the protobuf server")
    connections.values.foreach(_.close())
    Option(me).foreach(_.shutdown())
    becomeStopped()
  }

  private def updateConnectionStatus(activePeers: Int, brokerId: Option[PeerId]): Unit = {
    updateConnectionStatus(activePeers)
    updateConnectionStatus(brokerId)
  }

  private def updateConnectionStatus(activePeers: Int): Unit = {
    properties.activePeers.set(activePeers)
  }

  private def updateConnectionStatus(brokerId: Option[PeerId]): Unit = {
    properties.brokerId.set(brokerId)
  }

  private def publishingAddress(id: PeerId, listener: ActorRef): Receive = {
    case AddressPublicationResult(Success(_)) =>
      new InitializedServer(id, listener).start()

    case AddressPublicationResult(Failure(error)) =>
      log.error(error, "Address publication failed, retrying")
      publishAddress().onComplete { case result =>
        self ! AddressPublicationResult(result)
      }
  }

  private def bindPeer(id: PeerId, localPort: Int, listener: ActorRef): Future[Peer] = Future {
    val bindings = new Bindings()
    acceptedNetworkInterfaces.map(_.getName).foreach(bindings.addInterface)
    val ifaces = bindings.getInterfaces.mkString(",")
    log.info(s"Initiating a peer on port $localPort for interfaces $ifaces")

    val peer = new PeerMaker(createNumber160(id))
      .setPorts(localPort)
      .setBindings(bindings)
      .makeAndListen()
    peer.getConfiguration.setBehindFirewall(true)
    peer.setObjectDataReply(IncomingDataListener)
    peer.getPeerBean.getPeerMap.addPeerMapChangeListener(PeerChangeListener)

    peer
  }

  private def publishAddress(): Future[FutureDHT] = me.put(me.getPeerID)
    .setData(new Data(me.getPeerAddress.toByteArray))
    .start()

  private def connectToBroker(ownId: PeerId, brokerAddress: BrokerAddress, listener: ActorRef): Receive = {
    (for {
      resolvedBroker <- resolveBrokerAddress(brokerAddress)
      bootstrapResult <- bootstrap(resolvedBroker)
      broker = bootstrapResult.getBootstrapTo.head
      _ <- publishAddress()
    } yield createPeerId(broker)).pipeTo(self)

    val waitForBootstrap: Receive = {
      case brokerId: PeerId =>
        log.info("Successfully connected as {} using broker in {} with {}",
          ownId, brokerAddress, brokerId)
        new InitializedServer(brokerId, listener).start()

      case Status.Failure(error) =>
        log.error(error, "Cannot connect as {} using broker in {}, retrying in {}",
          ownId, brokerAddress, connectionRetryInterval)
        context.system.scheduler.scheduleOnce(connectionRetryInterval, self, RetryConnection)

      case RetryConnection =>
        become(connectToBroker(ownId, brokerAddress, listener))
    }

    waitForBootstrap orElse manageConnectionStatus
  }

  private def bootstrap(broker: InetSocketAddress): Future[FutureBootstrap] =
    bootstrapWithDiscover(broker).recoverWith {
      case NonFatal(cause) =>
        log.warning("TomP2P bootstrap with discover failed: " + cause)
        log.warning("Attempting bootstrap without discover")
        standaloneBootstrap(broker)
    }

  private def bootstrapWithDiscover(broker: InetSocketAddress): Future[FutureBootstrap] = for {
    discovery <- discover(broker)
    brokerAddress = discovery.getReporter
    bootstrap <- {
      log.info(s"Attempting connection to Coinffeine after discovery with broker at $brokerAddress")
      me.bootstrap ()
        .setPeerAddress(brokerAddress)
        .start ()
    }
  } yield bootstrap

  private def discover(broker: InetSocketAddress): Future[FutureDiscover] = me.discover()
    .setInetAddress(broker.getAddress)
    .setPorts(broker.getPort)
    .start()

  private def standaloneBootstrap(broker: InetSocketAddress): Future[FutureBootstrap] = {
    log.info(s"Attempting connection to Coinffeine using broker at $broker")
    me.getConfiguration.setBehindFirewall(false)
    me.bootstrap()
      .setInetAddress(broker.getAddress)
      .setPorts(broker.getPort)
      .start()
  }

  private def resolveBrokerAddress(address: BrokerAddress): Future[InetSocketAddress] = Future {
    new InetSocketAddress(InetAddress.getByName(address.hostname), address.port)
  }

  private class InitializedServer(brokerId: PeerId, listener: ActorRef) {

    def start(): Unit = {
      updateConnectionStatus(me.getPeerBean.getPeerMap.getAll.size(), Some(brokerId))
      become(handlingMessages orElse manageConnectionStatus)
    }

    private val handlingMessages: Receive = {
      case SendProtoMessage(to: PeerId, msg) => sendMessage(to, msg)
      case SendProtoMessage(BrokerId, msg) => sendMessage(brokerId, msg)
      case SendProtoMessageToBroker(msg) => sendMessage(brokerId, msg)
      case ReceiveData(from, data) =>
        val msg = CoinffeineMessage.parseFrom(data)
        val source = if (from == brokerId) BrokerId else from
        listener ! ReceiveProtoMessage(source, msg)
      case ResetConnection(peer) =>
        log.debug("Reset connection to peer {}", peer)
        clearConnection(peer)
    }

    private def sendMessage(to: PeerId, msg: CoinffeineMessage): Unit = for {
      peerAddress <- resolveAddress(to)
    } yield {
      me.sendDirect(getOrCreateConnection(peerAddress))
        .setObject(msg.toByteArray)
        .start()
        .onFailure { case err => self ! ResetConnection(peerAddress) }
    }
  }

  private def getOrCreateConnection(to: PeerAddress): PeerConnection = connections.get(to) match {
    case Some(connection) if !connection.isClosed => connection
    case _ => createConnection(to)
  }

  private def resolveAddress(peer: PeerId): Future[PeerAddress] =
    me.get(createNumber160(peer)).start().map { dhtEntry =>
      new PeerAddress(dhtEntry.getData.getData)
    }

  private def clearConnection(peer: PeerAddress): Unit = {
    connections.get(peer).foreach(_.close())
    connections -= peer
  }

  private def createConnection(peer: PeerAddress): PeerConnection = {
    clearConnection(peer)
    val connection = Option(me.createPeerConnection(peer, IdleTCPMillisTimeout))
      .getOrElse(throw new IllegalStateException(s"Could not create connection to $peer"))
    connections += peer -> connection
    connection
  }

  private def manageConnectionStatus: Receive = {
    case PeerMapChanged =>
      updateConnectionStatus(me.getPeerBean.getPeerMap.getAll.size())
  }

  private object IncomingDataListener extends ObjectDataReply {
    override def reply(sender: PeerAddress, request: Any): AnyRef = {
      self ! ReceiveData(createPeerId(sender), request.asInstanceOf[Array[Byte]])
      null
    }
  }

  /** Notifies the actor about changes on the peer map */
  private object PeerChangeListener extends PeerMapChangeListener {

    override def peerInserted(peerAddress: PeerAddress): Unit = {
      self ! PeerMapChanged
    }

    override def peerRemoved(peerAddress: PeerAddress): Unit = {
      self ! PeerMapChanged
    }

    override def peerUpdated(peerAddress: PeerAddress): Unit = {
      self ! PeerMapChanged
    }
  }

  private def createPeerId(tomp2pId: Number160): PeerId = PeerId(tomp2pId.toString.substring(2))
  private def createPeerId(address: PeerAddress): PeerId = createPeerId(address.getID)
  private def createNumber160(peerId: PeerId) = new Number160("0x" + peerId.value)
}

private[gateway] object ProtobufServerActor {
  private case class AddressPublicationResult(result: Try[FutureDHT])
  private case object PeerMapChanged
  private case class ReceiveData(from: PeerId, data: Array[Byte])
  private case object RetryConnection
  private case class ResetConnection(peer: PeerAddress)

  private val IdleTCPMillisTimeout = 6.minutes.toMillis.toInt

  def props(properties: MutableCoinffeineNetworkProperties,
            ignoredNetworkInterfaces: Seq[NetworkInterface],
            connectionRetryInterval: FiniteDuration): Props = Props(
    new ProtobufServerActor(properties, ignoredNetworkInterfaces, connectionRetryInterval))

  /** Send a message to a peer */
  case class SendProtoMessage(to: NodeId, msg: CoinffeineMessage)
  /** Send a message to the broker */
  case class SendProtoMessageToBroker(msg: CoinffeineMessage)

  /** Sent to the listener when a message is received */
  case class ReceiveProtoMessage(senderId: NodeId, msg: CoinffeineMessage)
}
