package coinffeine.overlay.test

import java.io.IOException

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.overlay.{OverlayId, OverlayNetwork}

private[this] class ClientActor(server: ActorRef) extends Actor {

  override def receive: Receive = disconnected

  private def disconnected: Receive = cannotSend orElse {
    case OverlayNetwork.Join(id) =>
      server ! ServerActor.Connect(id)
      context.become(connecting(id, sender()))
  }

  private def connecting(id: OverlayId, listener: ActorRef): Receive = cannotSend orElse {
    case OverlayNetwork.Join(newId) =>
      sender() ! OverlayNetwork.JoinFailed(newId, OverlayNetwork.AlreadyJoining)

    case ServerActor.Connected =>
      listener ! OverlayNetwork.Joined(id)
      context.become(connected(id, listener))

    case ServerActor.ConnectionRejected =>
      val cause = OverlayNetwork.UnderlyingNetworkFailure(new IOException("Injected error"))
      listener ! OverlayNetwork.JoinFailed(id, cause)
      context.become(disconnected)
  }

  private def connected(id: OverlayId, listener: ActorRef): Receive = alreadyJoined orElse {
    case OverlayNetwork.SendMessage(target, message) =>
      server ! ServerActor.SendMessage(target, message)

    case ServerActor.ReceiveMessage(source, message) =>
      listener ! OverlayNetwork.ReceiveMessage(source, message)

    case OverlayNetwork.Leave =>
      server ! ServerActor.Disconnect
      context.become(disconnecting(id, listener))

    case ServerActor.Disconnected =>
      val cause = OverlayNetwork.UnexpectedLeave(new Exception("Injected disconnection"))
      listener ! OverlayNetwork.Leaved(id, cause)
      context.become(disconnected)

    case ServerActor.NetworkSize(networkSize) =>
      listener ! OverlayNetwork.NetworkStatus(networkSize)
  }

  private def disconnecting(id: OverlayId, listener: ActorRef): Receive =
    alreadyJoined orElse cannotSend orElse {
      case ServerActor.Disconnected =>
        listener ! OverlayNetwork.Leaved(id, OverlayNetwork.RequestedLeave)
        context.become(disconnected)
    }

  private val alreadyJoined: Receive = {
    case OverlayNetwork.Join(newId) =>
      sender() ! OverlayNetwork.JoinFailed(newId, OverlayNetwork.AlreadyJoined)
  }

  private val cannotSend: Receive = {
    case sendRequest: OverlayNetwork.SendMessage =>
      sender() ! OverlayNetwork.CannotSend(sendRequest, OverlayNetwork.UnavailableNetwork)
  }
}

private object ClientActor {
  def props(server: ActorRef) = Props(new ClientActor(server))
}
