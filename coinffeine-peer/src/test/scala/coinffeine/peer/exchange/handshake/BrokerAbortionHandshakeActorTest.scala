package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

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
    gateway.relayMessageFromBroker(ExchangeAborted(exchange.id, "test abortion"))
    listener.expectMsgType[HandshakeFailure].cause.toString should include ("test abortion")
  }

  it should "terminate the handshake" in {
    listener.expectTerminated(actor)
  }
}
