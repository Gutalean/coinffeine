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

import coinffeine.model.event.CoinffeineConnectionStatus
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.proto.ProtoMessageGateway.ReceiveProtoMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

private class ProtobufServerActor(ignoredNetworkInterfaces: Seq[NetworkInterface])
  extends Actor with ActorLogging {

  import coinffeine.protocol.gateway.proto.ProtobufServerActor._
  import context.dispatcher

  private val acceptedNetworkInterfaces = NetworkInterface.getNetworkInterfaces
    .filterNot(ignoredNetworkInterfaces.contains)

  private var me: Peer = _
  private var connectionStatus = CoinffeineConnectionStatus(activePeers = 0, brokerId = None)

  override def preStart(): Unit = {
    publishConnectionStatusEvent()
  }

  override def postStop(): Unit = {
    log.info("Shutting down the protobuf server")
    Option(me).map(_.shutdown())
  }

  override val receive: Receive = waitingForInitialization orElse manageConnectionStatus

  private def waitingForInitialization: Receive = {
    case JoinAsBroker(port) =>
      initPeer(port, sender())
      publishAddress().onComplete { case result =>
       self ! AddressPublicationResult(result)
      }
      context.become(publishingAddress(sender()) orElse manageConnectionStatus)

    case JoinAsPeer(port, broker) =>
      initPeer(port, sender())
      connectToBroker(createPeerId(me), broker, sender())
  }

  private def publishingAddress(listener: ActorRef): Receive = {
    case AddressPublicationResult(Success(_)) =>
      val myId = createPeerId(me)
      listener ! Joined(ownId = myId, brokerId = myId)
      new InitializedServer(listener, myId).start()

    case AddressPublicationResult(Failure(error)) =>
      listener ! JoinError(error)
      me = null
      context.become(receive)
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
    me.setObjectDataReply(new ReplyHandler(listener))
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

  private def connectToBroker(
      ownId: PeerId, brokerAddress: BrokerAddress, listener: ActorRef): Unit = {
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
        log.info(s"Successfully connected as $ownId using broker in $brokerAddress with $brokerId")
        listener ! Joined(ownId, brokerId)
        new InitializedServer(listener, brokerId).start()

      case Status.Failure(error) =>
        log.error(s"Cannot connect as $ownId using broker in $brokerAddress: $error")
        listener ! JoinError(error)
        context.become(receive)
    }

    context.become(waitForBootstrap orElse manageConnectionStatus)
  }

  private class InitializedServer(listener: ActorRef, brokerId: PeerId) {

    def start(): Unit = {
      connectionStatus = CoinffeineConnectionStatus(
        activePeers = me.getPeerBean.getPeerMap.getAll.size(),
        brokerId = Some(brokerId)
      )
      publishConnectionStatusEvent()
      context.become(sendingMessages orElse manageConnectionStatus)
    }

    private val sendingMessages: Receive = {
      case SendMessage(to, msg) =>
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

  private class ReplyHandler(listener: ActorRef) extends ObjectDataReply {
    override def reply(sender: PeerAddress, request: Any): AnyRef = {
      context.dispatcher.execute(new Runnable {
        override def run(): Unit = {
          val msg = CoinffeineMessage.parseFrom(request.asInstanceOf[Array[Byte]])
          listener ! ReceiveProtoMessage(msg, createPeerId(sender))
        }
      })
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

  def props(ignoredNetworkInterfaces: Seq[NetworkInterface]): Props = Props(
    new ProtobufServerActor(ignoredNetworkInterfaces))

  /** Send a message to a peer */
  case class SendMessage(to: PeerId, msg: CoinffeineMessage)
}
