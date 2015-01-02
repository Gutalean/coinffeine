package coinffeine.overlay.test

import akka.actor._
import akka.util.ByteString

import coinffeine.overlay.OverlayId

private[this] class ServerActor extends Actor with ActorLogging {

  private var connections = Map.empty[OverlayId, ActorRef]

  override def receive: Receive = {
    case ServerActor.Connect(id) => connections += id -> sender()
    case ServerActor.Disconnect => connections = connections.filter(_._2 == sender())
    case ServerActor.SendMessage(to, message) => sendMessage(sender(), to, message)
  }

  private def sendMessage(senderRef: ActorRef, receiverId: OverlayId, message: ByteString): Unit = {
    (findIdFor(senderRef), connections.get(receiverId)) match {
      case (None, _) =>
        log.warning("Dropping message from not connected source {}: {}", senderRef, message)
      case (_, None) =>
        log.warning("Dropping message for not connected target {}: {}", receiverId, message)
      case (Some(senderId), Some(receiverRef)) =>
        receiverRef ! ServerActor.ReceiveMessage(senderId, message)
    }
  }

  private def findIdFor(ref: ActorRef): Option[OverlayId] = connections.collectFirst {
    case (id, `ref`) => id
  }
}

private object ServerActor {
  val props = Props[ServerActor]
  case class Connect(id: OverlayId)
  case object Disconnect
  case class SendMessage(to: OverlayId, message: ByteString)
  case class ReceiveMessage(from: OverlayId, message: ByteString)
}
