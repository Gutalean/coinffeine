package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.peer.ProtocolConstants
import coinffeine.protocol.messages.handshake.PeerHandshake

class ReRequestRefundHandshakeActorTest extends HandshakeActorTest("happy-path") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitRefundSignatureTimeout = 500 millis,
    refundSignatureAbortTimeout = 1 minute
  )

  "The handshake actor" should "request refund transaction signature after a timeout" in {
    givenActorIsInitialized()
    givenActorIsSubscribedToMessages()
    givenCounterpartPeerHandshake()
    gateway.expectForwardingPF(counterpartId) {
      case _: PeerHandshake =>
    }
    shouldCreateDeposits()
    shouldForwardRefundSignatureRequest()
    gateway.expectSubscription()
    shouldForwardRefundSignatureRequest()
  }

  it should "request it again after signing counterpart refund" in {
    shouldSignCounterpartRefund()
    shouldForwardRefundSignatureRequest()
  }
}
