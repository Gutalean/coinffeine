package coinffeine.overlay.relay.client

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress}
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.io.{IO, Tcp}
import akka.util.ByteString

import coinffeine.overlay.relay.client.RelayNetwork._
import coinffeine.overlay.relay.messages._
import coinffeine.overlay.relay.settings.RelayClientSettings
import coinffeine.overlay.{OverlayId, OverlayNetwork}

private[this] class ClientActor(settings: RelayClientSettings, tcpManager: ActorRef)
  extends Actor with ActorLogging {

  private class Connection(
      val id: OverlayId,
      val socket: ActorRef,
      val listener: ActorRef) {
    var buffer = ByteString.empty

    def append(data: ByteString): Unit = { buffer ++= data }

    @tailrec
    final def decodeFrames(onMessage: Message => Unit,
                           onInvalidData: InvalidDataReceived => Unit): Unit = {
      Frame.deserialize(buffer, settings.maxFrameBytes) match {
        case Frame.IncompleteInput => // Stop for the moment

        case Frame.Parsed(Frame(protobuf), remainingBuffer) =>
          buffer = remainingBuffer
          Try(ProtobufConversion.fromProtobuf(protobuf)) match {
            case Success(message) =>
              onMessage(message)
              decodeFrames(onMessage, onInvalidData)
            case Failure(ex) =>
              onInvalidData(InvalidDataReceived("Cannot parse protobuf", ex))
          }

        case Frame.FailedParsing(message) =>
          onInvalidData(InvalidDataReceived(s"Cannot delimit frame: $message"))
      }
    }
  }

  override def receive: Receive = disconnected

  private def disconnected: Receive = notSendingMessages orElse {
    case OverlayNetwork.Join(id) =>
      resolveAddress() match {
        case Success(remoteAddress) =>
          becomeConnecting(id, remoteAddress, sender())

        case Failure(ex) =>
          val cause = new IOException(s"Cannot resolve ${settings.host}:${settings.port}", ex)
          sender() ! OverlayNetwork.JoinFailed(id, OverlayNetwork.UnderlyingNetworkFailure(cause))
      }
  }

  private def resolveAddress(): Try[InetSocketAddress] =
    Try(InetAddress.getByName(settings.host)).map { host =>
      new InetSocketAddress(host, settings.port)
    }

  private def becomeConnecting(id: OverlayId,
                               remoteAddress: InetSocketAddress,
                               listener: ActorRef): Unit = {
    tcpManager ! Tcp.Connect(remoteAddress, timeout = Some(settings.connectionTimeout))
    context.become(notSendingMessages orElse {
      case OverlayNetwork.Join(otherId) =>
        sender() ! OverlayNetwork.JoinFailed(otherId, OverlayNetwork.AlreadyJoining)

      case Tcp.Connected(_, _) =>
        becomeConnected(new Connection(id, socket = sender(), listener))

      case Tcp.CommandFailed(_: Tcp.Connect) =>
        val cause = OverlayNetwork.UnderlyingNetworkFailure(CannotStartConnection(remoteAddress))
        listener ! OverlayNetwork.JoinFailed(id, cause)
        context.become(disconnected)
    })
  }

  private def becomeConnected(connection: Connection): Unit = {
    log.info("Connected as {} to {}:{}", connection.id, settings.host, settings.port)

    connection.socket ! Tcp.Register(self)
    connection.socket ! Tcp.Write(ProtobufFrame.serialize(JoinMessage(connection.id)))
    connection.listener ! OverlayNetwork.Joined(connection.id)

    def handleMessage(message: Message): Unit = {
      message match {
        case StatusMessage(networkSize) =>
          connection.listener ! OverlayNetwork.NetworkStatus(networkSize)
        case RelayMessage(senderId, payload) =>
          connection.listener ! OverlayNetwork.ReceiveMessage(senderId, payload)
        case joinMessage: JoinMessage =>
          val cause = InvalidDataReceived(s"Unexpected message received: $joinMessage")
          becomeDisconnecting(connection, OverlayNetwork.UnexpectedLeave(cause))
      }
    }

    context.become(alreadyJoined orElse {
      case request @ OverlayNetwork.SendMessage(to, message) =>
        val frameBytes = ProtobufFrame.serialize(RelayMessage(to, message))
        if (frameBytes.size > settings.maxFrameBytes) {
          val cause = MessageTooLarge(frameBytes.size, settings.maxFrameBytes - Frame.HeaderSize)
          connection.listener ! OverlayNetwork.CannotSend(request, OverlayNetwork.UnderlyingNetworkFailure(cause))
        } else {
          connection.socket ! Tcp.Write(frameBytes)
        }

      case Tcp.Received(data) =>
        connection.append(data)
        connection.decodeFrames(
          onMessage = handleMessage,
          onInvalidData = error => becomeDisconnecting(connection, OverlayNetwork.UnexpectedLeave(error))
        )

      case closed: Tcp.ConnectionClosed =>
        log.error("Connection to {}:{} closed unexpectedly: {}", settings.host, settings.port,
          closed.getErrorCause)
        val cause = OverlayNetwork.UnexpectedLeave(
          UnexpectedConnectionTermination(closed.getErrorCause))
        connection.listener ! OverlayNetwork.Leaved(connection.id, cause)
        context.become(disconnected)

      case OverlayNetwork.Leave =>
        becomeDisconnecting(connection, OverlayNetwork.RequestedLeave)
    })
  }

  private def becomeDisconnecting(connection: Connection, cause: OverlayNetwork.LeaveCause): Unit = {
    connection.socket ! Tcp.Close
    context.become(alreadyJoined orElse notSendingMessages orElse {
      case _: Tcp.ConnectionClosed =>
        log.info("Connection to {}:{} successfully closed", settings.host, settings.port)
        connection.listener ! OverlayNetwork.Leaved(connection.id, cause)
        context.become(disconnected)
    })
  }

  private def alreadyJoined: Receive = {
    case OverlayNetwork.Join(otherId) =>
      sender() ! OverlayNetwork.JoinFailed(otherId, OverlayNetwork.AlreadyJoined)
  }

  private def notSendingMessages: Receive = {
    case request: OverlayNetwork.SendMessage =>
      sender() ! OverlayNetwork.CannotSend(request, OverlayNetwork.UnavailableNetwork)
  }
}

private object ClientActor {
  def props(settings: RelayClientSettings)(implicit system: ActorSystem): Props =
    props(settings, IO(Tcp))
  def props(settings: RelayClientSettings, tcpManager: ActorRef): Props =
    Props(new ClientActor(settings, tcpManager))
}
