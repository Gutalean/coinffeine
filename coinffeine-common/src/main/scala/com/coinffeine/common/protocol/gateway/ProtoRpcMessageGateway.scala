package com.coinffeine.common.protocol.gateway

import java.io.IOException
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.googlecode.protobuf.pro.duplex.PeerInfo
import io.netty.channel.ChannelFuture
import io.netty.util.concurrent.{Future, GenericFutureListener}

import com.coinffeine.common.PeerConnection
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.network.NetworkComponent
import com.coinffeine.common.protocol.gateway.MessageGateway._
import com.coinffeine.common.protocol.gateway.PeerIdResolverActor.{AddMapping, LookupMapping}
import com.coinffeine.common.protocol.gateway.PeerServiceImpl.ReceiveProtoMessage
import com.coinffeine.common.protocol.messages.PublicMessage
import com.coinffeine.common.protocol.protobuf.{CoinffeineProtobuf => proto}
import com.coinffeine.common.protocol.serialization.{ProtocolSerialization, ProtocolSerializationComponent}
import com.coinffeine.common.protorpc.{Callbacks, PeerServer, PeerSession}

private[gateway] class ProtoRpcMessageGateway(serialization: ProtocolSerialization)
  extends Actor with ActorLogging {

  import ProtoRpcMessageGateway._

  private val subscriptions = context.actorOf(SubscriptionManagerActor.props, "subscriptions")
  private val resolver = context.actorOf(PeerIdResolverActor.props, "resolver")
  private var server: PeerServer = _
  private var serverInfo: PeerInfo = _
  private var sessions = Map.empty[PeerConnection, PeerSession]

  override def postStop(): Unit = {
    log.info("Shutting down message gateway")
    server.shutdown()
  }

  override def receive = waitingForInitialization orElse managingSubscriptions

  private val managingSubscriptions: Receive = {
    case msg: Subscribe => subscriptions forward msg
    case msg @ Unsubscribe => subscriptions forward msg
    case Terminated(actor) => subscriptions.tell(Unsubscribe, actor)
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
      context.become(binding(startFuture, ownId, brokerId, brokerConnection, sender()) orElse
        managingSubscriptions)
  }

  private def startServer(address: PeerInfo, brokerConnection: PeerConnection): ChannelFuture = {
    serverInfo = address
    server =
      new PeerServer(serverInfo, proto.PeerService.newReflectiveService(new PeerServiceImpl(self)))
    val starting = server.start()
    starting.addListener(new GenericFutureListener[Future[_ >: Void]] {
      override def operationComplete(future: Future[_ >: Void]): Unit = self ! ServerStarted
    })
  }

  private def session(connection: PeerConnection): PeerSession = sessions.getOrElse(connection, {
    val s = server.peerWith(new PeerInfo(connection.hostname, connection.port)).get
    sessions += connection -> s
    s
  })

  private class StartedGateway(ownId: PeerId, brokerId: PeerId, brokerConnection: PeerConnection) {

    def start(): Unit = {
      context.become(forwardingMessages orElse managingSubscriptions)
      resolver ! PeerIdResolverActor.Start(brokerId, brokerConnection, self)
      log.info(s"Message gateway started on $serverInfo with id $ownId")
    }

    private val forwardingMessages: Receive = {
      case m @ ForwardMessage(msg, destId) =>
        forward(destId, msg)

      case ReceiveProtoMessage(protoMessage, senderConnection) =>
        val (message, senderId) = serialization.fromProtobuf(protoMessage)
        resolver ! AddMapping(senderId, senderConnection)
        subscriptions ! ReceiveMessage(message, senderId)

      case PeerWith(connection) =>
        sender() ! session(connection)
    }

    private def forward(to: PeerId, message: PublicMessage): Unit = {
      import context.dispatcher
      implicit val lookupTimeout = Timeout(10.seconds)
      (resolver ? LookupMapping(to)).mapTo[PeerConnection].onComplete {
        case Success(connection) => forward(connection, message)
        case Failure(cause) =>
          log.error(s"Cannot resolve peer with id $to, dropping $message")
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
  }
}

object ProtoRpcMessageGateway {

  private case object ServerStarted

  case class PeerWith(connection: PeerConnection)

  trait Component extends MessageGateway.Component {
    this: ProtocolSerializationComponent with NetworkComponent=>

    override lazy val messageGatewayProps = Props(new ProtoRpcMessageGateway(protocolSerialization))
  }
}
