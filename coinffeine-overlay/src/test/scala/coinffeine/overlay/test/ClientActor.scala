package coinffeine.overlay.test

import akka.actor.{Actor, ActorRef, Props}

import coinffeine.overlay.{OverlayId, OverlayNetwork}

private[this] class ClientActor(server: ActorRef) extends Actor {

  override def receive: Receive = disconnected

  private def disconnected: Receive = {
    case OverlayNetwork.Join(id) =>
      server ! ServerActor.Connect(id)
      sender() ! OverlayNetwork.Joined(id)
      context.become(connected(id, sender()))

    case sendRequest: OverlayNetwork.SendMessage =>
      sender() ! OverlayNetwork.CannotSend(sendRequest, OverlayNetwork.UnavailableNetwork)

  }

  private def connected(id: OverlayId, listener: ActorRef): Receive = {
    case OverlayNetwork.Join(newId) =>
      sender() ! OverlayNetwork.JoinFailed(newId, OverlayNetwork.AlreadyJoined)

    case OverlayNetwork.SendMessage(target, message) =>
      server ! ServerActor.SendMessage(target, message)

    case ServerActor.ReceiveMessage(source, message) =>
      listener ! OverlayNetwork.ReceiveMessage(source, message)

    case OverlayNetwork.Leave =>
      sender() ! OverlayNetwork.Leaved(id, OverlayNetwork.RequestedLeave)
      context.become(disconnected)
  }
}

private object ClientActor {
  def props(server: ActorRef) = Props(new ClientActor(server))
}
