package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.model.network.BrokerId
import coinffeine.peer.ProtocolConstants
import coinffeine.protocol.messages.handshake.{PeerHandshake, ExchangeRejection}

class RefundUnsignedHandshakeActorTest extends HandshakeActorTest("signature-timeout") {

  import HandshakeActor._

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 10 seconds,
    refundSignatureAbortTimeout = 100 millis
  )

  "Handshakes without our refund signed" should "be aborted after a timeout" in {
    gateway.expectForwardingPF(counterpartId) {
      case _: PeerHandshake =>
    }
    listener.expectMsgClass(classOf[HandshakeFailure])
    listener.expectTerminated(actor)
  }

  it must "notify the broker that the exchange is rejected" in {
    gateway.expectForwardingPF(BrokerId) {
      case ExchangeRejection(exchange.`id`, _) =>
    }
  }
}
