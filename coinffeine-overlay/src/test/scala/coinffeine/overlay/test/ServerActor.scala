package coinffeine.overlay.test

import scala.util.Random

import akka.actor._
import akka.util.ByteString

import coinffeine.overlay.OverlayId
import coinffeine.overlay.test.FakeOverlayNetwork.DelayDistribution

private[this] class ServerActor(messageDropRate: Double,
                                connectionFailureRate: Double,
                                delayDistribution: DelayDistribution)
    extends Actor with ActorLogging {

  private val generator = new Random()
  private var connections = Map.empty[OverlayId, ActorRef]

  override def receive: Receive = {
    case ServerActor.Connect(id) => handleConnectionRequest(id, sender())
    case ServerActor.Disconnect => connections = connections.filter(_._2 == sender())
    case ServerActor.SendMessage(to, message) => sendMessage(sender(), to, message)
  }

  private def handleConnectionRequest(clientId: OverlayId, clientRef: ActorRef): Unit = {
    if (shouldRejectConnection()) {
      clientRef ! ServerActor.ConnectionRejected
    } else {
      connections += clientId -> sender()
      clientRef ! ServerActor.Connected
    }
  }

  private def sendMessage(senderRef: ActorRef, receiverId: OverlayId, message: ByteString): Unit = {
    (findIdFor(senderRef), connections.get(receiverId)) match {
      case (None, _) =>
        log.warning("Dropping message from not connected source {}: {}", senderRef, message)
      case (_, None) =>
        log.warning("Dropping message for not connected target {}: {}", receiverId, message)
      case (Some(senderId), Some(_)) if shouldDropMessage() =>
        log.debug("Dropping message from {} to {}: {}", senderId, receiverId, message)
      case (Some(senderId), Some(receiverRef)) =>
        import context.dispatcher
        context.system.scheduler.scheduleOnce(delayDistribution.nextDelay()) {
          receiverRef ! ServerActor.ReceiveMessage(senderId, message)
        }
    }
  }

  private def findIdFor(ref: ActorRef): Option[OverlayId] = connections.collectFirst {
    case (id, `ref`) => id
  }

  private def shouldDropMessage(): Boolean = randomlyWithRate(messageDropRate)
  private def shouldRejectConnection(): Boolean = randomlyWithRate(connectionFailureRate)
  private def randomlyWithRate(rate: Double) = generator.nextDouble() < rate
}

private object ServerActor {
  def props(messageDropRate: Double,
            connectionFailureRate: Double,
            delayDistribution: DelayDistribution) =
    Props(new ServerActor(messageDropRate, connectionFailureRate, delayDistribution))

  case class Connect(id: OverlayId)
  case object Connected
  case object ConnectionRejected
  case object Disconnect
  case class SendMessage(to: OverlayId, message: ByteString)
  case class ReceiveMessage(from: OverlayId, message: ByteString)
}
