package coinffeine.peer.exchange.util

import scala.concurrent.Future

import akka.actor.{ActorContext, ActorRef}
import akka.dispatch.ExecutionContexts
import akka.pattern.pipe

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Role, Exchange}
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway.ForwardMessage
import coinffeine.protocol.messages.PublicMessage

class MessageForwarding(messageGateway: ActorRef, counterpart: PeerId, broker: PeerId) {

  def this(messageGateway: ActorRef, exchange: Exchange[FiatCurrency], role: Role) =
    this(messageGateway, exchange.counterpartId, exchange.brokerId)

  def forwardToCounterpart(message: PublicMessage): Unit =
    forwardMessage(message, counterpart)

  def forwardToCounterpart(message: Future[PublicMessage])(implicit context: ActorContext): Unit =
    forwardMessage(message, counterpart)

  def forwardToBroker(message: PublicMessage): Unit =
    forwardMessage(message, broker)

  def forwardToBroker(message: Future[PublicMessage])(implicit context: ActorContext): Unit =
    forwardMessage(message, broker)

  def forwardMessage(message: PublicMessage, address: PeerId): Unit =
    messageGateway ! ForwardMessage(message, address)

  def forwardMessage(message: Future[PublicMessage], address: PeerId)
                    (implicit context: ActorContext): Unit = {
    implicit val executionContext = ExecutionContexts.global()
    message.map(ForwardMessage(_, address)).pipeTo(messageGateway)(context.self)
  }
}
