package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import akka.testkit._

import coinffeine.model.network.BrokerId
import coinffeine.peer.ProtocolConstants
import coinffeine.protocol.messages.handshake.{PeerHandshake, ExchangeRejection}

class RefundUnsignedHandshakeActorTest extends DefaultHandshakeActorTest("signature-timeout") {

  import HandshakeActor._

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 10.seconds.dilated,
    refundSignatureAbortTimeout = 100.millis.dilated
  )

  "Handshakes without our refund signed" should "be aborted after a timeout" in {
    listener.expectMsgType[HandshakeFailure]
  }

  it must "notify the broker that the exchange is rejected" in {
    gateway.expectForwardingToPF(counterpartId) {
      case _: PeerHandshake =>
    }

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
