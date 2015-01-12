package coinffeine.overlay.relay.server

import java.io.IOException
import java.net.InetSocketAddress

import akka.actor._
import akka.io.{IO, Tcp}

import coinffeine.common.akka.ServiceActor
import coinffeine.overlay.OverlayId
import coinffeine.overlay.relay.messages.{StatusMessage, RelayMessage}

/** Actor implementing the server-side of the relay protocol. */
private[this] class ServerActor(tcpManager: ActorRef)
  extends Actor with ServiceActor[ServerConfig] with ActorLogging {

  private var socket = ActorRef.noSender
  private var workers = Set.empty[ActorRef]
  private var activeIds = Map.empty[OverlayId, ActorRef]

  override protected def starting(config: ServerConfig): Receive = {
    val localAddress = new InetSocketAddress(config.bindAddress, config.bindPort)
    tcpManager ! Tcp.Bind(self, localAddress)
    handle {
      case Tcp.Bound(_) =>
        socket = sender()
        becomeStarted(started(config))
      case Tcp.CommandFailed(_: Tcp.Bind) => cancelStart(ServerActor.CannotBind(localAddress))
    }
  }

  private def started(config: ServerConfig): Receive = {

    def spawnWorkerActor(remoteAddress: InetSocketAddress, connection: ActorRef): ActorRef = {
      val connectionWorker = context.actorOf(ServerWorkerActor.props(
        ServerWorkerActor.Connection(remoteAddress, connection), config))
      workers += connectionWorker
      context.watch(connectionWorker)
      connectionWorker
    }

    handle {
      case Tcp.Connected(remoteAddress, _) =>
        val connection = sender()
        connection ! Tcp.Register(spawnWorkerActor(remoteAddress, connection))

      case ServerWorkerActor.JoinAs(idInUse) if activeIds.contains(idInUse) =>
        sender() ! ServerWorkerActor.CannotJoinWithIdentifierInUse

      case ServerWorkerActor.JoinAs(unusedId) =>
        activeIds += unusedId -> sender()
        notifyStatus()
        sender() ! ServerWorkerActor.Joined(StatusMessage(networkSize))

      case RelayMessage(to, payload) =>
        val from = findId(sender()).get
        activeIds.get(to).fold(droppingMessageFor(to)) { ref =>
          ref ! RelayMessage(from, payload)
        }

      case Terminated(terminatedWorker) =>
        findId(terminatedWorker).foreach { id =>
          activeIds -= id
          notifyStatus()
        }
        workers -= terminatedWorker
    }
  }

  override protected def stopping(): Receive = {
    activeIds = Map.empty
    if (workers.isEmpty) {
      socket ! Tcp.Unbind
    } else {
      workers.foreach(_ ! PoisonPill)
    }
    handle {
      case Terminated(terminatedWorker) =>
        workers -= terminatedWorker
        if (workers.isEmpty) {
          socket ! Tcp.Unbind
        }
      case Tcp.Unbound => becomeStopped()
    }
  }

  private def droppingMessageFor(to: OverlayId): Unit = {
    log.warning("Cannot deliver message to disconnected {}", to)
  }

  private def findId(ref: ActorRef): Option[OverlayId] =
    activeIds.collectFirst {
      case (id, `ref`) => id
    }

  private def notifyStatus(): Unit = {
    val status = StatusMessage(networkSize)
    context.children.foreach(_ ! status)
  }

  private def networkSize: Int = activeIds.size
}

object ServerActor {
  case class CannotBind(bindAddress: InetSocketAddress)
    extends IOException(s"Cannot bind to address $bindAddress")

  def props(implicit system: ActorSystem): Props = props(IO(Tcp))
  def props(tcpManager: ActorRef): Props = Props(new ServerActor(tcpManager))
}
