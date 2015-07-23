package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.model.bitcoin._
import coinffeine.model.exchange.HandshakeFailureCause
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.handshake.HandshakeActor.HandshakeFailure
import coinffeine.protocol.messages.handshake.PeerHandshake


class InvalidCounterpartWalletHandshakeActorTest extends HandshakeActorTest("invalid-wallet") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 1.minute,
    refundSignatureAbortTimeout = 1.minute
  )

  "Invalid counterpart wallet ids" should "make the handshake to fail" in {
    givenCounterpartWalletIdIsInvalid()
    shouldForwardPeerHandshake()
    val peerHandshake =
      PeerHandshake(exchange.id, counterpart.bitcoinKey.publicKey, counterpart.paymentProcessorAccount)
    gateway.relayMessage(peerHandshake, counterpartId)
    listener.expectMsgType[HandshakeFailure].cause shouldBe
        HandshakeFailureCause.InvalidCounterpartAccountId
  }

  it should "terminate the handshake under request" in {
    actor ! HandshakeActor.Finish
    listener.expectTerminated(actor)
  }
}
