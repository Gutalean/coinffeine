package coinffeine.overlay.relay.server

import java.net.InetSocketAddress

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.io.Tcp
import akka.util.ByteString

import coinffeine.common.akka.LimitedRateProxy
import coinffeine.overlay.OverlayId
import coinffeine.overlay.relay.messages._

private class ServerWorkerActor(connection: ServerWorkerActor.Connection, config: ServerConfig)
  extends Actor with ActorLogging {

  private case object IdentificationTimeout

  private var buffer = ByteString.empty

  override def receive = waitingForIdentification

  private def waitingForIdentification: Receive = {
    import context.dispatcher
    val identificationTimeout =
      context.system.scheduler.scheduleOnce(config.identificationTimeout, self, IdentificationTimeout)
    val behavior: Receive = {
      case Tcp.Received(data) =>
        buffer = decodeFrame(buffer ++ data) {
          case JoinMessage(newId) =>
            identificationTimeout.cancel()
            becomeJoining(newId)
        }
      case IdentificationTimeout =>
        disconnectBecauseOf(s"Client at ${connection.remoteAddress} has not identified " +
          s"itself after ${config.identificationTimeout}")
    }
    behavior
  }

  private def becomeJoining(id: OverlayId): Unit = {
    context.parent ! ServerWorkerActor.JoinAs(id)
    context.become {
      case ServerWorkerActor.CannotJoinWithIdentifierInUse =>
        disconnectBecauseOf(s"$id is already in use")

      case ServerWorkerActor.Joined(status) =>
        log.info("{} connected as {}", connection.remoteAddress, id)
        becomeIdentified(id, status)
    }
  }

  private def becomeIdentified(id: OverlayId, initialStatus: StatusMessage): Unit = {
    val statusSender = context.actorOf(LimitedRateProxy.props(
      connection.ref, config.minTimeBetweenStatusUpdates))
    statusSender ! Tcp.Write(ProtobufFrame.serialize(initialStatus))

    context.become {
      case status: StatusMessage =>
        statusSender ! Tcp.Write(ProtobufFrame.serialize(status))

      case _: Tcp.ConnectionClosed => context.stop(self)

      case Tcp.Received(data) =>
        buffer = decodeFrames(buffer ++ data) {
          case message: RelayMessage => context.parent ! message
        }

      case message: RelayMessage => write(message)
    }
  }

  @tailrec
  private def decodeFrames(buffer: ByteString)
                          (handler: PartialFunction[Message, Unit]): ByteString = {
    val remainingBuffer = decodeFrame(buffer)(handler)
    if (remainingBuffer == buffer) remainingBuffer
    else decodeFrames(remainingBuffer)(handler)
  }

  private def decodeFrame(buffer: ByteString)
                         (handler: PartialFunction[Message, Unit]): ByteString = {
    Frame.deserialize(buffer, config.maxFrameBytes) match {
      case Frame.IncompleteInput => buffer

      case Frame.Parsed(Frame(protobuf), remainingBuffer) =>
        Try(ProtobufConversion.fromProtobuf(protobuf)) match {
          case Success(message) if handler.isDefinedAt(message) =>
            handler(message)
            remainingBuffer

          case Success(unexpectedMessage) =>
            disconnectBecauseOf(s"unexpected message ($unexpectedMessage)")
            ByteString.empty

          case Failure(ex) =>
            disconnectBecauseOf("invalid protobuf received", ex)
            ByteString.empty
        }

      case Frame.FailedParsing(error) =>
        disconnectBecauseOf(s"invalid frame received: $error")
        ByteString.empty
    }
  }

  private def disconnectBecauseOf(description: String, cause: Throwable = null): Unit = {
    log.error(cause, "Closing connection with {}: {}", connection.remoteAddress, description)
    connection.ref ! Tcp.Close
    context.stop(self)
  }

  private def write(message: Message): Unit = {
    connection.ref ! Tcp.Write(ProtobufFrame.serialize(message))
  }
}

private object ServerWorkerActor {
  case class Connection(remoteAddress: InetSocketAddress, ref: ActorRef)
  case class JoinAs(id: OverlayId)
  case object CannotJoinWithIdentifierInUse
  case class Joined(status: StatusMessage)

  def props(connection: Connection, config: ServerConfig): Props =
    Props(new ServerWorkerActor(connection, config))
}
