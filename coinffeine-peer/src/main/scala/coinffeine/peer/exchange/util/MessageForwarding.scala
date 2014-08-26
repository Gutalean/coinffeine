package coinffeine.peer.exchange.util

import akka.actor.ActorRef

import coinffeine.model.network.{BrokerId, PeerId}
import coinffeine.protocol.gateway.MessageGateway.ForwardMessage
import coinffeine.protocol.messages.PublicMessage

class MessageForwarding(messageGateway: ActorRef, counterpart: PeerId) {

  def forwardToCounterpart(message: PublicMessage): Unit = {
    forwardMessage(message, counterpart)
  }

  def forwardToBroker(message: PublicMessage): Unit = {
    messageGateway ! ForwardMessage(message, BrokerId)
  }

  def forwardMessage(message: PublicMessage, address: PeerId): Unit = {
    messageGateway ! ForwardMessage(message, address)
  }
}
