package coinffeine.protocol.gateway.proto

import java.net.{InetAddress, NetworkInterface}
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.{Failure, Random, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import net.tomp2p.connection.Bindings
import net.tomp2p.futures.{FutureBootstrap, FutureDHT}
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.peers.{Number160, PeerAddress}
import net.tomp2p.rpc.ObjectDataReply
import net.tomp2p.storage.Data

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.gateway.proto.ProtoMessageGateway.ReceiveProtoMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

private class ProtobufServerActor(
    ignoredNetworkInterfaces: Seq[NetworkInterface]) extends Actor with ActorLogging {
  import context.dispatcher
  import ProtobufServerActor._

  private var me: Peer = _

  override def postStop(): Unit = {
    log.info("Shutting down the protobuf server")
    Option(me).map(_.shutdown())
  }

  override val receive: Receive = {
    case bind: Bind =>
      val listener = sender()
      initPeer(bind.listenToPort, listener)
      publishAddress().onComplete {
        case Success(_) =>
          listener ! Bound(createPeerId(me))
          context.become(sendingMessages)
        case Failure(error) =>
          listener ! BindingError(error)
          me = null
      }
    case connect: Connect =>
      val listener = sender()
      initPeer(connect.localPort, listener)
      connectToBroker(createPeerId(me), connect.connectTo, listener)
  }

  def initPeer(localPort: Int, listener: ActorRef): Unit = {
    val bindings = new Bindings()
    NetworkInterface.getNetworkInterfaces
      .filterNot(ignoredNetworkInterfaces.contains)
      .map(_.getName)
      .foreach(iface => bindings.addInterface(iface))
    val ifaces = bindings.getInterfaces.mkString(",")
    log.info(s"Initiating a peer on port $localPort for interfaces $ifaces")
    me = new PeerMaker(Number160.createHash(Random.nextInt()))
      .setPorts(localPort)
      .setBindings(bindings)
      .makeAndListen()
    me.setObjectDataReply(new ReplyHandler(listener))
  }

  private def publishAddress(): Future[FutureDHT] = me.put(me.getPeerID)
    .setData(new Data(me.getPeerAddress.toByteArray))
    .start()

  private def bootstrap(brokerAddress: BrokerAddress): Future[FutureBootstrap] = me.bootstrap()
    .setInetAddress(InetAddress.getByName(brokerAddress.hostname))
    .setPorts(brokerAddress.port)
    .start()

  private def bootstrapBroadcast(brokerAddress: BrokerAddress): Future[FutureBootstrap] = me.bootstrap()
    .setBroadcast()
    .setPorts(brokerAddress.port)
    .start()

  private def connectToBroker(
      ownId: PeerId, brokerAddress: BrokerAddress, listener: ActorRef): Unit = {
    import context.dispatcher

    val futureBrokerId = for {
      future <- bootstrap(brokerAddress)
      _ <- publishAddress()
    } yield createPeerId(future.getBootstrapTo.head)

    futureBrokerId.onComplete {
      case Success(brokerId) =>
        listener ! Connected(ownId, brokerId)
        context.become(sendingMessages)
      case Failure(error) =>
        listener ! ConnectingError(error)
        log.info(s"Couldn't connect to Coinffeine network as $ownId to broker in $brokerAddress")
        context.become(receive)
    }
    context.become(Map.empty)
  }

  private def sendingMessages: Receive = {
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

  private def createPeerId(tomp2pId: Number160): PeerId = PeerId(tomp2pId.toString)
  private def createPeerId(address: PeerAddress): PeerId = createPeerId(address.getID)
  private def createPeerId(peer: Peer): PeerId = createPeerId(peer.getPeerID)
  private def createNumber160(peerId: PeerId) = new Number160(peerId.value)
}

private[gateway] object ProtobufServerActor {
  def props(ignoredNetworkInterfaces: Seq[NetworkInterface]): Props = Props(
    new ProtobufServerActor(ignoredNetworkInterfaces))

  /** Send a message to a peer */
  case class SendMessage(to: PeerId, msg: CoinffeineMessage)
}
