package com.coinffeine.common.protocol.gateway

import java.io.IOException

import akka.actor._
import com.google.protobuf.{RpcCallback, RpcController}
import com.googlecode.protobuf.pro.duplex.PeerInfo
import com.googlecode.protobuf.pro.duplex.execute.ServerRpcController
import io.netty.channel.ChannelFuture
import io.netty.util.concurrent.{Future, GenericFutureListener}

import com.coinffeine.common.PeerConnection
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.network.NetworkComponent
import com.coinffeine.common.protocol.gateway.MessageGateway._
import com.coinffeine.common.protocol.messages.PublicMessage
import com.coinffeine.common.protocol.protobuf.{CoinffeineProtobuf => proto}
import com.coinffeine.common.protocol.protobuf.CoinffeineProtobuf.PeerIdResolution
import com.coinffeine.common.protocol.serialization.{ProtocolSerialization, ProtocolSerializationComponent}
import com.coinffeine.common.protorpc.{Callbacks, PeerServer, PeerSession}

private[gateway] class ProtoRpcMessageGateway(serialization: ProtocolSerialization)
  extends Actor with ActorLogging {

  import ProtoRpcMessageGateway._

  /** Metadata on message subscription requested by an actor. */
  private case class MessageSubscription(filter: Filter)

  private class PeerServiceImpl extends proto.PeerService.Interface {

    override def sendMessage(controller: RpcController,
                             message: proto.CoinffeineMessage,
                             done: RpcCallback[proto.Void]): Unit = {
      val (parsedMessage, senderId) = serialization.fromProtobuf(message)
      self ! PeerIdResolved(senderId, clientPeerConnection(controller))
      dispatchToSubscriptions(parsedMessage, senderId)
      done.run(VoidResponse)
    }

    private def clientPeerConnection(controller: RpcController) = {
      val info = controller.asInstanceOf[ServerRpcController].getRpcClient.getServerInfo
      PeerConnection(info.getHostName, info.getPort)
    }

    override def resolvePeerId(controller: RpcController, request: proto.PeerId,
                               callback: RpcCallback[PeerIdResolution]): Unit = {
      self ! ResolvePeerId(PeerId(request.getPeerId), callback)
    }
  }

  private var server: PeerServer = _
  private var serverInfo: PeerInfo = _
  private var subscriptions = Map.empty[ActorRef, MessageSubscription]
  private var sessions = Map.empty[PeerConnection, PeerSession]

  override def postStop(): Unit = {
    log.info("Shutting down message gateway")
    server.shutdown()
  }

  override def receive = waitingForInitialization orElse managingSubscriptions

  private val managingSubscriptions: Receive = {
    case Subscribe(filter) =>
      subscriptions += sender -> MessageSubscription(filter)
    case Unsubscribe =>
      subscriptions -= sender
    case Terminated(actor) =>
      subscriptions -= actor
  }

  private def binding(startFuture: ChannelFuture, ownId: PeerId, brokerId: PeerId,
                      brokerConnection: PeerConnection, listener: ActorRef): Receive = {

    case ServerStarted if startFuture.isSuccess =>
      listener ! BoundTo(PeerConnection(serverInfo.getHostName, serverInfo.getPort))
      new StartedGateway(ownId, brokerId, brokerConnection).start()

    case ServerStarted =>
      server.shutdown()
      listener ! BindingError(startFuture.cause())
      log.info(s"Message gateway couldn't start at $serverInfo")
      context.become(waitingForInitialization orElse managingSubscriptions)
  }

  private val waitingForInitialization: Receive = {
    case Bind(ownId, peerConnection, brokerId, brokerConnection) =>
      val address = new PeerInfo(peerConnection.hostname, peerConnection.port)
      val startFuture = startServer(address, brokerConnection)
      context.become(binding(startFuture, ownId, brokerId, brokerConnection, sender()) orElse managingSubscriptions)

    case ResolvePeerId(id, callback) =>
      log.warning(s"Cannot resolve $id during initialization")
      respondResolutionCallback(connectionOpt = None, callback)
  }

  private def startServer(address: PeerInfo, brokerConnection: PeerConnection): ChannelFuture = {
    serverInfo = address
    server = new PeerServer(serverInfo, proto.PeerService.newReflectiveService(new PeerServiceImpl()))
    val starting = server.start()
    starting.addListener(new GenericFutureListener[Future[_ >: Void]] {
      override def operationComplete(future: Future[_ >: Void]): Unit = self ! ServerStarted
    })
  }

  private def dispatchToSubscriptions(msg: PublicMessage, sender: PeerId): Unit = {
    val notification = ReceiveMessage(msg, sender)
    for ((actor, MessageSubscription(filter)) <- subscriptions if filter(notification)) {
      actor ! notification
    }
  }

  private def session(connection: PeerConnection): PeerSession = sessions.getOrElse(connection, {
    val s = server.peerWith(new PeerInfo(connection.hostname, connection.port)).get
    sessions += connection -> s
    s
  })

  private class StartedGateway(ownId: PeerId, brokerId: PeerId, brokerConnection: PeerConnection) {

    private var peerMap: Map[PeerId, PeerConnection] = Map(brokerId -> brokerConnection)
    private var pendingMessages = Map.empty[PeerId, Seq[PublicMessage]].withDefaultValue(Seq.empty)

    def start(): Unit = {
      context.become(forwardingMessages orElse managingSubscriptions)
      log.info(s"Message gateway started on $serverInfo with id $ownId")
    }

    private val forwardingMessages: Receive = {
      case m @ ForwardMessage(msg, destId) =>
        forward(destId, msg)

      case ResolvePeerId(id, callback) =>
        respondResolutionCallback(peerMap.get(id), callback)

      case PeerIdResolved(id, connection) =>
        peerMap += id -> connection
        pendingMessages(id).foreach(message => forward(id, message))
        pendingMessages -= id
    }

    private def forward(to: PeerId, message: PublicMessage): Unit = {
      if (peerMap.contains(to)) forward(peerMap(to), message)
      else {
        pendingMessages += to -> (pendingMessages(to) :+ message)
        resolvePeerId(to)
      }
    }

    private def forward(to: PeerConnection, message: PublicMessage): Unit = {
      try {
        val s = session(to)
        proto.PeerService.newStub(s.channel).sendMessage(
          s.controller,
          serialization.toProtobuf(message, ownId),
          Callbacks.noop[proto.Void]
        )
      } catch {
        case e: IOException =>
          throw ForwardException(s"cannot forward message $message to $to: ${e.getMessage}", e)
      }
    }

    private def resolvePeerId(peerId: PeerId): Unit = {
      log.info(s"Asking the broker to resolve $peerId")
      try {
        val s = session(brokerConnection)
        proto.PeerService.newStub(s.channel).resolvePeerId(
          s.controller,
          proto.PeerId.newBuilder.setPeerId(peerId.value).build(),
          new RpcCallback[proto.PeerIdResolution] {
            override def run(resolution: proto.PeerIdResolution): Unit = {
              if (resolution.hasPeerConnection) {
                self ! PeerIdResolved(peerId, PeerConnection.parse(resolution.getPeerConnection))
              } else {
                log.error(s"Cannot resolve $peerId, the broker doesn't know it")
              }
            }
          }
        )
      } catch {
        case e: IOException => throw ForwardException(s"cannot ask for $peerId resolution", e)
      }
    }
  }

  /** Resolve a peer id resolution.
    *
    * @param connectionOpt  Associated PeerConnection or None if unknown
    * @param callback       Callback to invoke
    */
  private def respondResolutionCallback(connectionOpt: Option[PeerConnection],
                                        callback: RpcCallback[PeerIdResolution]): Unit = {
    val response = PeerIdResolution.newBuilder
    connectionOpt.foreach { c => response.setPeerConnection(c.toString)}
    callback.run(response.build())
  }
}

object ProtoRpcMessageGateway {

  private[protocol] val VoidResponse = proto.Void.newBuilder().build()

  private case object ServerStarted

  private case class ResolvePeerId(peerId: PeerId, callback: RpcCallback[PeerIdResolution])

  private case class PeerIdResolved(peerId: PeerId, peerConnection: PeerConnection)

  trait Component extends MessageGateway.Component {
    this: ProtocolSerializationComponent with NetworkComponent=>

    override lazy val messageGatewayProps = Props(new ProtoRpcMessageGateway(protocolSerialization))
  }
}
