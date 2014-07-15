package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, Subscribe}
import coinffeine.protocol.messages.handshake.PeerHandshake
import com.coinffeine.common.ProtocolConstants

class ReRequestRefundHandshakeActorTest extends HandshakeActorTest("happy-path") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitRefundSignatureTimeout = 500 millis,
    refundSignatureAbortTimeout = 1 minute
  )

  "The handshake actor" should "request refund transaction signature after a timeout" in {
    givenActorIsInitialized()
    gateway.expectMsgAllClassOf(classOf[Subscribe], classOf[ForwardMessage[PeerHandshake]])
    givenCounterpartPeerHandshake()
    shouldForwardRefundSignatureRequest()
    shouldForwardRefundSignatureRequest()
  }

  it should "request it again after signing counterpart refund" in {
    shouldSignCounterpartRefund()
    shouldForwardRefundSignatureRequest()
  }
}
