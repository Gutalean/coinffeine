package coinffeine.overlay.test

import scala.util.Random

import akka.actor._
import akka.util.ByteString

import coinffeine.overlay.OverlayId
import coinffeine.overlay.test.FakeOverlayNetwork.DelayDistribution

private[this] class ServerActor(messageDropRate: Double,
                                connectionFailureRate: Double,
                                delayDistribution: DelayDistribution,
                                disconnectionDistribution: DelayDistribution)
    extends Actor with ActorLogging {
  import context.dispatcher

  private case class Connection(ref: ActorRef, dropTimer: Option[Cancellable])
  private case class DropConnection(id: OverlayId)

  private val generator = new Random()
  private var connections = Map.empty[OverlayId, Connection]

  override def receive: Receive = {
    case ServerActor.Connect(id) => connect(id, sender())
    case ServerActor.Disconnect => disconnect(sender())
    case ServerActor.SendMessage(to, message) => sendMessage(sender(), to, message)
    case DropConnection(id) => dropConnection(id)
  }

  private def connect(clientId: OverlayId, clientRef: ActorRef): Unit = {
    if (shouldRejectConnection()) {
      clientRef ! ServerActor.ConnectionRejected
    } else {
      connections += clientId -> Connection(sender(), scheduleConnectionDrop(clientId))
      clientRef ! ServerActor.Connected(connections.size)
      notifyNetworkSize(clientId)
    }
  }

  private def scheduleConnectionDrop(clientId: OverlayId): Option[Cancellable] =
    disconnectionDistribution.nextDelay().map { delay =>
      context.system.scheduler.scheduleOnce(delay, receiver = self, DropConnection(clientId))
    }

  private def sendMessage(senderRef: ActorRef, receiverId: OverlayId, message: ByteString): Unit = {
    (findIdFor(senderRef), connections.get(receiverId).map(_.ref)) match {
      case (None, _) =>
        log.warning("Dropping message from not connected source {}: {}", senderRef, message)
      case (_, None) =>
        log.warning("Dropping message for not connected target {}: {}", receiverId, message)
      case (Some(senderId), Some(_)) if shouldDropMessage() =>
        log.debug("Dropping message from {} to {}: {}", senderId, receiverId, message)
      case (Some(senderId), Some(receiverRef)) =>
        delayDistribution.nextDelay().foreach { delay =>
          context.system.scheduler.scheduleOnce(delay) {
            receiverRef ! ServerActor.ReceiveMessage(senderId, message)
          }
        }
    }
  }

  private def disconnect(clientRef: ActorRef): Unit = {
    val maybeClientId = connections.collectFirst {
      case (clientId, Connection(`clientRef`, scheduledDisconnection)) =>
        scheduledDisconnection.foreach(_.cancel())
        clientRef ! ServerActor.Disconnected
        clientId
    }
    maybeClientId.foreach { disconnectedId =>
      connections -= disconnectedId
      notifyNetworkSize()
    }
  }

  private def dropConnection(id: OverlayId): Unit = {
    connections.get(id).foreach { connection =>
      connection.ref ! ServerActor.Disconnected
      connections -= id
      notifyNetworkSize()
    }
  }

  private def findIdFor(ref: ActorRef): Option[OverlayId] = connections.collectFirst {
    case (id, Connection(`ref`, _)) => id
  }

  private def notifyNetworkSize(exclusions: OverlayId*): Unit = {
    val notification = ServerActor.NetworkSize(connections.size)
    for (connection <- connections.values if !exclusions.contains(connection)) {
      connection.ref ! notification
    }
  }

  private def shouldDropMessage(): Boolean = randomlyWithRate(messageDropRate)
  private def shouldRejectConnection(): Boolean = randomlyWithRate(connectionFailureRate)
  private def randomlyWithRate(rate: Double) = generator.nextDouble() < rate
}

private object ServerActor {
  def props(messageDropRate: Double,
            connectionFailureRate: Double,
            delayDistribution: DelayDistribution,
            disconnectionDistribution: DelayDistribution) = Props(new ServerActor(
    messageDropRate, connectionFailureRate, delayDistribution, disconnectionDistribution))

  case class Connect(id: OverlayId)
  case class Connected(networkSize: Int)
  case object ConnectionRejected
  case object Disconnect
  case object Disconnected
  case class SendMessage(to: OverlayId, message: ByteString)
  case class ReceiveMessage(from: OverlayId, message: ByteString)
  case class NetworkSize(value: Int)
}
