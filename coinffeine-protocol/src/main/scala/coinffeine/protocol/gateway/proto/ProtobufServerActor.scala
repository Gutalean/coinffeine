package coinffeine.protocol.gateway.proto

import java.net.{InetAddress, NetworkInterface}
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util._

import akka.actor._
import akka.pattern._
import net.tomp2p.connection.Bindings
import net.tomp2p.futures.{FutureBootstrap, FutureDHT, FutureDiscover}
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.peers.{Number160, PeerAddress, PeerMapChangeListener}
import net.tomp2p.rpc.ObjectDataReply
import net.tomp2p.storage.Data

import coinffeine.common.akka.ServiceActor
import coinffeine.model.event.CoinffeineConnectionStatus
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

private class ProtobufServerActor(ignoredNetworkInterfaces: Seq[NetworkInterface])
  extends Actor with ServiceActor[Join] with ActorLogging {

  import coinffeine.protocol.gateway.proto.ProtobufServerActor._
  import context.dispatcher

  private val acceptedNetworkInterfaces = NetworkInterface.getNetworkInterfaces
    .filterNot(ignoredNetworkInterfaces.contains)

  private var me: Peer = _
  private var connectionStatus = CoinffeineConnectionStatus(activePeers = 0, brokerId = None)

  override protected def starting(args: Join): Receive = {
    publishConnectionStatusEvent()
    becomeStarted(args match {
      case JoinAsBroker(port) =>
        initPeer(port, sender())
        publishAddress().onComplete { case result =>
          self ! AddressPublicationResult(result)
        }
        publishingAddress(sender()) orElse manageConnectionStatus

      case JoinAsPeer(port, broker) =>
        initPeer(port, sender())
        connectToBroker(createPeerId(me), broker, sender())
    })
  }

  override protected def stopping(): Receive = {
    log.info("Shutting down the protobuf server")
    Option(me).map(_.shutdown())
    becomeStopped()
  }

  private def publishingAddress(listener: ActorRef): Receive = {
    case AddressPublicationResult(Success(_)) =>
      val myId = createPeerId(me)
      new InitializedServer(myId, listener).start()

    case AddressPublicationResult(Failure(error)) =>
      log.error(error, "Address publication failed, retrying")
      publishAddress().onComplete { case result =>
        self ! AddressPublicationResult(result)
      }
  }

  def initPeer(localPort: Int, listener: ActorRef): Unit = {
    val bindings = new Bindings()
    acceptedNetworkInterfaces.map(_.getName).foreach(bindings.addInterface)
    val ifaces = bindings.getInterfaces.mkString(",")
    log.info(s"Initiating a peer on port $localPort for interfaces $ifaces")

    me = new PeerMaker(Number160.createHash(Random.nextInt()))
      .setPorts(localPort)
      .setBindings(bindings)
      .makeAndListen()
    me.getConfiguration.setBehindFirewall(true)
    me.setObjectDataReply(IncomingDataListener)
    me.getPeerBean.getPeerMap.addPeerMapChangeListener(PeerChangeListener)
  }

  private def publishAddress(): Future[FutureDHT] = me.put(me.getPeerID)
    .setData(new Data(me.getPeerAddress.toByteArray))
    .start()

  private def discover(brokerAddress: BrokerAddress): Future[FutureDiscover] = me.discover()
    .setInetAddress(InetAddress.getByName(brokerAddress.hostname))
    .setPorts(brokerAddress.port)
    .start()

  private def bootstrap(brokerAddress: PeerAddress): Future[FutureBootstrap] = me.bootstrap()
    .setPeerAddress(brokerAddress)
    .start()

  private def bootstrap(brokerAddress: BrokerAddress): Future[FutureBootstrap] = me.bootstrap()
    .setInetAddress(InetAddress.getByName(brokerAddress.hostname))
    .setPorts(brokerAddress.port)
    .start()

  private def connectToBroker(ownId: PeerId, brokerAddress: BrokerAddress, listener: ActorRef): Receive = {
    val bootstrapFuture = discover(brokerAddress).map({ dis =>
      val realIp = dis.getPeerAddress
      log.info(s"Attempting connection to Coinffeine using IP $realIp")
      dis.getReporter
    }).flatMap(bootstrap).recoverWith {
      case err =>
        log.warning("TomP2P bootstrap with discover failed: " + err)
        log.warning("Attempting bootstrap without discover")
        bootstrap(brokerAddress)
    }

    (for {
      bs <- bootstrapFuture
      _ <- publishAddress()
    } yield {
      val brokerId = bs.getBootstrapTo.head
      log.info(s"Bootstrapped to $brokerId")
      createPeerId(bs.getBootstrapTo.head)
    }).pipeTo(self)

    val waitForBootstrap: Receive = {
      case brokerId: PeerId =>
        log.info("Successfully connected as {} using broker in {} with {}",
          ownId, brokerAddress, brokerId)
        new InitializedServer(brokerId, listener).start()

      case Status.Failure(error) =>
        log.error(error, "Cannot connect as {} using broker in {}, retrying", ownId, brokerAddress)
        become(connectToBroker(ownId, brokerAddress, listener))
    }

    waitForBootstrap orElse manageConnectionStatus
  }

  private class InitializedServer(brokerId: PeerId, listener: ActorRef) {

    def start(): Unit = {
      connectionStatus = CoinffeineConnectionStatus(
        activePeers = me.getPeerBean.getPeerMap.getAll.size(),
        brokerId = Some(brokerId)
      )
      publishConnectionStatusEvent()
      become(handlingMessages orElse manageConnectionStatus)
    }

    private val handlingMessages: Receive = {
      case SendProtoMessage(to, msg) => sendMessage(to, msg)
      case SendProtoMessageToBroker(msg) => sendMessage(brokerId, msg)
      case ReceiveData(from, data) =>
        val msg = CoinffeineMessage.parseFrom(data)
        val fromBroker = from == brokerId
        listener ! ReceiveProtoMessage(from, msg, fromBroker)
    }

    private def sendMessage(to: PeerId, msg: CoinffeineMessage) = {
      val sendMsg = me.get(createNumber160(to)).start().flatMap(dhtEntry => {
        me.sendDirect(new PeerAddress(dhtEntry.getData.getData))
          .setObject(msg.toByteArray)
          .start()
      })
      sendMsg.onFailure { case err =>
        log.error(err, s"Failure when sending send message $msg to $to")
      }
    }
  }

  private def manageConnectionStatus: Receive = {
    case MessageGateway.RetrieveConnectionStatus =>
      sender() ! connectionStatus

    case PeerMapChanged =>
      connectionStatus = connectionStatus.copy(activePeers = me.getPeerBean.getPeerMap.getAll.size())
      publishConnectionStatusEvent()
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

  private def publishConnectionStatusEvent(): Unit = {
    context.system.eventStream.publish(connectionStatus)
  }

  private def createPeerId(tomp2pId: Number160): PeerId = PeerId(tomp2pId.toString)
  private def createPeerId(address: PeerAddress): PeerId = createPeerId(address.getID)
  private def createPeerId(peer: Peer): PeerId = createPeerId(peer.getPeerID)
  private def createNumber160(peerId: PeerId) = new Number160(peerId.value)
}

private[gateway] object ProtobufServerActor {
  private case class AddressPublicationResult(result: Try[FutureDHT])
  private case object PeerMapChanged
  private case class ReceiveData(from: PeerId, data: Array[Byte])

  def props(ignoredNetworkInterfaces: Seq[NetworkInterface]): Props = Props(
    new ProtobufServerActor(ignoredNetworkInterfaces))

  /** Send a message to a peer */
  case class SendProtoMessage(to: PeerId, msg: CoinffeineMessage)
  /** Send a message to the broker */
  case class SendProtoMessageToBroker(msg: CoinffeineMessage)

  /** Sent to the listener when a message is received */
  case class ReceiveProtoMessage(senderId: PeerId, msg: CoinffeineMessage, fromBroker: Boolean)
}
