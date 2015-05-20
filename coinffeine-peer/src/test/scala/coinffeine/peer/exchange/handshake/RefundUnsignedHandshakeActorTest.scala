package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.model.network.BrokerId
import coinffeine.peer.ProtocolConstants
import coinffeine.protocol.messages.handshake.{PeerHandshake, ExchangeRejection}

class RefundUnsignedHandshakeActorTest extends DefaultHandshakeActorTest("signature-timeout") {

  import HandshakeActor._

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 10 seconds,
    refundSignatureAbortTimeout = 100 millis
  )

  "Handshakes without our refund signed" should "be aborted after a timeout" in {
    gateway.expectForwardingToPF(counterpartId) {
      case _: PeerHandshake =>
    }
    listener.expectMsgClass(classOf[HandshakeFailure])
  }

  it must "notify the broker that the exchange is rejected" in {
    val id = exchange.id
    gateway.expectForwardingToPF(BrokerId) {
      case ExchangeRejection(`id`, _) =>
    }
  }

  it must "terminate under request" in {
    actor ! HandshakeActor.Finish
    listener.expectTerminated(actor)
  }
}
