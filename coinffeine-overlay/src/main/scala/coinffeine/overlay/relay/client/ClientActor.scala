package coinffeine.overlay.relay.client

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress}
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
    private var buffer = ByteString.empty

    def append(data: ByteString): Unit = { buffer ++= data }

    /** Decode frames and stop if the buffer is consumed or an error happens.
      *
      * @param onMessage      Handler for received messages
      * @param onInvalidData  Handler for invalid data received
      */
    def decodeFrames(onMessage: Message => Unit,
                     onInvalidData: InvalidDataReceived => Unit): Unit =
      while(decodeFrame(onMessage, onInvalidData)) {}

    /** Decode at most a frame.
      *
      * @param onMessage      Handler for received messages
      * @param onInvalidData  Handler for invalid data received
      * @return               True if there is more unprocessed buffer
      */
    def decodeFrame(onMessage: Message => Unit,
                    onInvalidData: InvalidDataReceived => Unit): Boolean =
      Frame.deserialize(buffer, settings.maxFrameBytes) match {
        case Frame.IncompleteInput => false
        case Frame.Parsed(Frame(protobuf), remainingBuffer) =>
          buffer = remainingBuffer
          Try(ProtobufConversion.fromProtobuf(protobuf)) match {
            case Success(message) =>
              onMessage(message)
              true
            case Failure(ex) =>
              onInvalidData(InvalidDataReceived("Cannot parse protobuf", ex))
              false
          }
        case Frame.FailedParsing(message) =>
          onInvalidData(InvalidDataReceived(s"Cannot delimit frame: $message"))
          false
      }
  }

  override def receive: Receive = disconnected

  private def disconnected: Receive = notSendingMessages orElse {
    case OverlayNetwork.Join(id) =>
      resolveAddress() match {
        case Success(remoteAddress) =>
          log.debug("Joining relay network at {} as {}", remoteAddress, id)
          becomeConnecting(id, remoteAddress, sender())

        case Failure(ex) =>
          log.debug("Join failure: cannot resolve {}", settings.host)
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
        if (otherId == id) log.error("Already joining as {}", id)
        else log.error("Cannot join as {}, already joining as {} ", otherId, id)
        sender() ! OverlayNetwork.JoinFailed(otherId, OverlayNetwork.AlreadyJoining)

      case Tcp.Connected(_, _) =>
        log.debug("TCP connection with {} succeeded, start handshaking", remoteAddress)
        becomeHandshaking(new Connection(id, socket = sender(), listener))

      case Tcp.CommandFailed(_: Tcp.Connect) =>
        log.error("TCP connection with {} failed", remoteAddress)
        val cause = OverlayNetwork.UnderlyingNetworkFailure(CannotStartConnection(remoteAddress))
        listener ! OverlayNetwork.JoinFailed(id, cause)
        context.become(disconnected)
    })
  }

  private def becomeHandshaking(connection: Connection): Unit = {
    import context.dispatcher
    object IdentificationTimeout
    val timeout = context.system.scheduler.scheduleOnce(
      settings.identificationTimeout, self, IdentificationTimeout)

    log.info("Connected to {}:{}, identifying as {}", settings.host, settings.port, connection.id)

    connection.socket ! Tcp.Register(self)
    connection.socket ! Tcp.Write(ProtobufFrame.serialize(JoinMessage(connection.id)))

    def onMessage(message: Message): Unit = message match {
      case StatusMessage(networkSize) =>
        timeout.cancel()
        connection.listener ! OverlayNetwork.Joined(
          connection.id, OverlayNetwork.NetworkStatus(networkSize))
        becomeJoined(connection)

      case unexpectedMessage =>
        timeout.cancel()
        abortHandshake(s"invalid message of type ${unexpectedMessage.getClass}")
    }

    def onInvalidData(invalidData: InvalidDataReceived): Unit = {
      abortHandshake(invalidData.message)
    }

    def abortHandshake(errorMessage: String): Unit = {
      log.error("Handshake failure: {}", errorMessage)
      val cause = OverlayNetwork.UnderlyingNetworkFailure(HandshakeFailed(errorMessage))
      connection.listener ! OverlayNetwork.JoinFailed(connection.id, cause)
      connection.socket ! Tcp.Close
      context.become(disconnected)
    }

    context.become(notSendingMessages orElse {
      case IdentificationTimeout =>
        abortHandshake(s"server didn't respond in ${settings.identificationTimeout}")

      case Tcp.Received(data) =>
        log.debug("Received {} bytes", data.size)
        connection.append(data)
        connection.decodeFrame(onMessage, onInvalidData)
    })
  }

  private def becomeJoined(connection: Connection): Unit = {
    def handleMessages(): Unit = {
      connection.decodeFrames(
        onMessage = {
          case StatusMessage(networkSize) =>
            log.debug("Network size of {}", networkSize)
            connection.listener ! OverlayNetwork.NetworkStatus(networkSize)
          case RelayMessage(senderId, payload) =>
            log.debug("Message from {} of size {}", senderId, payload.size)
            connection.listener ! OverlayNetwork.ReceiveMessage(senderId, payload)
          case joinMessage: JoinMessage =>
            val cause = InvalidDataReceived(s"Unexpected message received: $joinMessage")
            becomeDisconnecting(connection, OverlayNetwork.UnexpectedLeave(cause))
        },
        onInvalidData = error =>
          becomeDisconnecting(connection, OverlayNetwork.UnexpectedLeave(error))
      )
    }

    log.info("Joined as {} to {}:{}", connection.id, settings.host, settings.port)
    handleMessages()
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
        handleMessages()

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
    log.debug("Disconnecting because of {}", cause)
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
