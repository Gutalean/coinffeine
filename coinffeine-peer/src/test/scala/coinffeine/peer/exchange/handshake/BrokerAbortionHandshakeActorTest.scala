package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.model.exchange.HandshakeFailureCause
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.protocol.messages.handshake.ExchangeAborted

class BrokerAbortionHandshakeActorTest extends HandshakeActorTest("broker-aborts") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 1.minute,
    refundSignatureAbortTimeout = 1.minute
  )

  "Handshakes aborted by the broker" should "make the handshake to fail" in {
    shouldForwardPeerHandshake()
    gateway.relayMessageFromBroker(ExchangeAborted(exchange.id, ExchangeAborted.Timeout))
    listener.expectMsgType[HandshakeFailure].cause shouldBe HandshakeFailureCause.BrokerAbortion
  }

  it should "terminate the handshake under request" in {
    actor ! HandshakeActor.Finish
    listener.expectTerminated(actor)
  }
}
