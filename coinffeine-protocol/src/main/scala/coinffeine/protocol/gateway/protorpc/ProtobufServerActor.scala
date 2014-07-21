package coinffeine.protocol.gateway.protorpc

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.googlecode.protobuf.pro.duplex.PeerInfo
import io.netty.channel.{Channel, ChannelFuture}
import io.netty.util.concurrent.{Future, GenericFutureListener}

import coinffeine.protocol.gateway.MessageGateway.{Bind, BindingError, BoundTo}
import coinffeine.protocol.gateway.PeerConnection
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}

private class ProtobufServerActor extends Actor with ActorLogging {
  import coinffeine.protocol.gateway.protorpc.ProtobufServerActor._

  private var server: PeerServer = _
  private var sessions = Map.empty[PeerConnection, PeerSession]
  private var serverChannel: Channel = _

  override def postStop(): Unit = {
    log.info("Shutting down the protobuf server")
    Option(server).foreach(_.shutdown())
    sessions.mapValues(_.close())
    Option(serverChannel).foreach(_.close())
  }

  override def receive: Receive = {
    case bind: Bind => context.become(binding(bind, sender()))
  }

  private def binding(bind: Bind, listener: ActorRef): Receive = {
    val address = new PeerInfo(bind.connection.hostname, bind.connection.port)
    val startFuture = startServer(address, bind.brokerConnection, listener)

    {
      case ServerStarted if startFuture.isSuccess =>
        listener ! BoundTo(PeerConnection(address.getHostName, address.getPort))
        serverChannel = startFuture.channel()
        context.become(managingSessions)

      case ServerStarted =>
        server.shutdown()
        listener ! BindingError(startFuture.cause())
        log.info(s"Message gateway couldn't start at $address")
        context.become(receive)
    }
  }

  private def startServer(address: PeerInfo, brokerConnection: PeerConnection,
                          listener: ActorRef): ChannelFuture = {
    server =
      new PeerServer(address, proto.PeerService.newReflectiveService(new PeerServiceImpl(listener)))
    val starting = server.start()
    starting.addListener(new GenericFutureListener[Future[_ >: Void]] {
      override def operationComplete(future: Future[_ >: Void]): Unit = self ! ServerStarted
    })
  }

  private val managingSessions: Receive = {
    case PeerWith(connection) => sender() ! session(connection)
  }

  private def session(connection: PeerConnection): PeerSession = sessions.getOrElse(connection, {
    val s = server.peerWith(new PeerInfo(connection.hostname, connection.port)).get
    sessions += connection -> s
    s
  })
}

private[gateway] object ProtobufServerActor {
  val props: Props = Props(new ProtobufServerActor())

  private case object ServerStarted

  /** Ask for a session. A PeerSession will be sent back. */
  case class PeerWith(connection: PeerConnection)
}
