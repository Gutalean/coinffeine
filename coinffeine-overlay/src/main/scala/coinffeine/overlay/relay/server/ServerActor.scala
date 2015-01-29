package coinffeine.overlay.relay.server

import java.io.IOException
import java.net.InetSocketAddress

import akka.actor._
import akka.io.{IO, Tcp}

import coinffeine.common.akka.ServiceLifecycle
import coinffeine.overlay.OverlayId
import coinffeine.overlay.relay.messages.{RelayMessage, StatusMessage}
import coinffeine.overlay.relay.settings.RelayServerSettings

/** Actor implementing the server-side of the relay protocol. */
private[this] class ServerActor(tcpManager: ActorRef)
  extends Actor with ServiceLifecycle[RelayServerSettings] with ActorLogging {

  private var socket = ActorRef.noSender
  private var workers = Set.empty[ActorRef]
  private var activeIds = Map.empty[OverlayId, ActorRef]

  override protected def onStart(settings: RelayServerSettings) = {
    val localAddress = new InetSocketAddress(settings.bindAddress, settings.bindPort)
    tcpManager ! Tcp.Bind(self, localAddress)
    BecomeStarting {
      case Tcp.Bound(_) =>
        socket = sender()
        completeStart(started(settings))
      case Tcp.CommandFailed(_: Tcp.Bind) => cancelStart(ServerActor.CannotBind(localAddress))
    }
  }

  private def started(settings: RelayServerSettings): Receive = {
    case Tcp.Connected(remoteAddress, _) =>
      val connection = sender()
      connection ! Tcp.Register(spawnWorkerActor(remoteAddress, connection, settings))

    case ServerWorkerActor.JoinAs(id) =>
      activeIds.get(id).foreach(_ ! ServerWorkerActor.Terminate)
      activeIds += id -> sender()
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

  private def spawnWorkerActor(remoteAddress: InetSocketAddress,
                               connection: ActorRef,
                               settings: RelayServerSettings): ActorRef = {
    val connectionWorker = context.actorOf(ServerWorkerActor.props(
      ServerWorkerActor.Connection(remoteAddress, connection), settings))
    workers += connectionWorker
    context.watch(connectionWorker)
    connectionWorker
  }

  override protected def onStop() = {
    activeIds = Map.empty
    if (workers.isEmpty) {
      socket ! Tcp.Unbind
    } else {
      workers.foreach(_ ! ServerWorkerActor.Terminate)
    }

    BecomeStopping {
      case Terminated(terminatedWorker) =>
        workers -= terminatedWorker
        if (workers.isEmpty) {
          socket ! Tcp.Unbind
        }
      case Tcp.Unbound => completeStop()
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
