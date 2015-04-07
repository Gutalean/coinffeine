package coinffeine.peer.exchange.handshake

import akka.actor._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.peer.exchange.handshake.CounterpartRefundSigner.StartSigningRefunds
import coinffeine.peer.exchange.protocol.Handshake
import coinffeine.peer.exchange.protocol.Handshake.InvalidRefundTransaction
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.handshake._

private class CounterpartRefundSigner(gateway: ActorRef,
                                      exchangeId: ExchangeId,
                                      counterpart: PeerId) extends Actor with ActorLogging {

  override def receive: Receive = {
    case StartSigningRefunds(handshake) =>
      log.info("Handshake {}: ready to sign counterpart refund", exchangeId)
      gateway ! Subscribe {
        case ReceiveMessage(RefundSignatureRequest(`exchangeId`, _), `counterpart`) =>
      }
      context.become(signingRefunds(handshake))
  }

  private def signingRefunds(handshake: Handshake[_ <: FiatCurrency]): Receive = {
    case ReceiveMessage(RefundSignatureRequest(_, refundTransaction), _) =>
      try {
        log.info("Handshake {}: signing refund TX {}", exchangeId,
          refundTransaction.get.getHashAsString)
        val refundSignature = handshake.signHerRefund(refundTransaction)
        gateway ! ForwardMessage(RefundSignatureResponse(exchangeId, refundSignature), counterpart)
      } catch {
        case cause: InvalidRefundTransaction =>
          log.warning("Handshake {}: Dropping invalid refund: {}", exchangeId, cause)
      }
  }

  override def postStop(): Unit = {
    log.info("Handshake {}: stop signing counterpart refunds", exchangeId)
  }
}

private[handshake] object CounterpartRefundSigner {
  def props(gateway: ActorRef, exchange: HandshakingExchange[_ <: FiatCurrency]): Props =
    Props(new CounterpartRefundSigner(gateway, exchange.id, exchange.counterpartId))

  case class StartSigningRefunds(exchange: Handshake[_ <: FiatCurrency])
}
