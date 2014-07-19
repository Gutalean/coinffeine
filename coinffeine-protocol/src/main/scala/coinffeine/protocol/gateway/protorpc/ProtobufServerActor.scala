package coinffeine.protocol.gateway.protorpc

import java.net.InetAddress
import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Random}

import akka.actor.{ActorRef, Actor, ActorLogging, Props}
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.peers.{PeerAddress, Number160}
import net.tomp2p.rpc.ObjectDataReply
import net.tomp2p.storage.Data

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway.{Bind, BindingError, Bound}
import coinffeine.protocol.gateway.protorpc.ProtoRpcMessageGateway.ReceiveProtoMessage
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

private class ProtobufServerActor extends Actor with ActorLogging {
  import context.dispatcher
  import ProtobufServerActor._

  private var me: Peer = _

  override def postStop(): Unit = {
    log.info("Shutting down the protobuf server")
    Option(me).map(_.shutdown())
  }

  override val receive: Receive = {
    case bind: Bind =>
      me = new PeerMaker(Number160.createHash(Random.nextInt()))
        .setPorts(bind.listenToPort)
        .makeAndListen()
      me.put(me.getPeerID).setData(new Data(me.getPeerAddress.toByteArray)).start()
      val listener = sender()
      me.setObjectDataReply(new ObjectDataReply {
        override def reply(sender: PeerAddress, request: Any): AnyRef = {
          context.dispatcher.execute(new Runnable {
            override def run(): Unit = {
              val msg = CoinffeineMessage.parseFrom(request.asInstanceOf[Array[Byte]])
              listener ! ReceiveProtoMessage(msg, createPeerId(sender))
            }
          })
          null
        }
      })
      if (bind.connectTo.isDefined) {
        val brokerAddress = bind.connectTo.get
        val bootstrapFuture = me.bootstrap()
          .setInetAddress(InetAddress.getByName(brokerAddress.hostname))
          .setPorts(brokerAddress.port)
          .start()
        import context.dispatcher
        bootstrapFuture.onComplete {
          case Success(future) =>
            val broker = future.getBootstrapTo.head
            succeedBinding(listener, createPeerId(broker))
          case Failure(error) =>
            listener ! BindingError(error)
            log.info(s"Couldn't connect to Coinffeine network using " +
              s"${brokerAddress.hostname}:${brokerAddress.port}")
            context.become(receive)
        }
        context.become(Map.empty)
      } else {
        succeedBinding(listener, createPeerId(me))
      }
  }

  private def succeedBinding(listener: ActorRef, brokerId: PeerId): Unit = {
    listener ! Bound(brokerId)
    context.become(sendingMessages)
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

  private def createPeerId(tomp2pId: Number160): PeerId = PeerId(tomp2pId.toString)
  private def createPeerId(address: PeerAddress): PeerId = createPeerId(address.getID)
  private def createPeerId(peer: Peer): PeerId = createPeerId(peer.getPeerID)
  private def createNumber160(peerId: PeerId) = new Number160(peerId.value)
}

private[gateway] object ProtobufServerActor {
  val props: Props = Props[ProtobufServerActor]

  /** Send a message to a peer */
  case class SendMessage(to: PeerId, msg: CoinffeineMessage)
}
