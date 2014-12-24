package coinffeine.protocol.gateway.proto

import scala.concurrent.Future

import akka.actor._
import akka.pattern._

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork

class ConnectionActor(session: P2PNetwork.Session, receiverId: PeerId)
  extends Actor with Stash with ActorLogging {

  import context.dispatcher
  import ConnectionActor._

  private var connection: Option[P2PNetwork.Connection] = None

  override def preStart(): Unit = startConnecting()
  override def postStop(): Unit = closeConnection()

  override def receive: Receive = connecting

  private def connecting: Receive = {
    case conn: P2PNetwork.Connection =>
      connection = Some(conn)
      context.become(ready)
      unstashAll()

    case Status.Failure(connectionError) => throw connectionError

    case _ => stash()
  }

  private def ready: Receive = {
    case Message(bytes) => becomeSendingUntil(connection.get.send(bytes))
    case Ping => becomeSendingUntil(connection.get.ping())
    case PingBack => becomeSendingUntil(connection.get.pingBack())
  }

  private def becomeSendingUntil(future: Future[Unit]): Unit = {
    future.map(_ => MessageDelivered).pipeTo(self)
    context.become(sending)
  }

  private def sending: Receive = {
    case MessageDelivered =>
      context.become(ready)
      unstashAll()

    case Status.Failure(cause) =>
      log.error(cause, "Send failure. Connection being reset")
      closeConnection()
      startConnecting()
      context.become(connecting)

    case _ => stash()
  }

  private def startConnecting(): Unit = {
    session.connect(receiverId).pipeTo(self)
  }

  private def closeConnection(): Unit = {
    connection.foreach(_.close())
  }
}

object ConnectionActor {
  case class Message(bytes: Array[Byte])
  case object Ping
  case object PingBack
  private case object MessageDelivered
}
