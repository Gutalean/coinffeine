package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.model.Both
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.blockchain.BlockchainActor._
import coinffeine.peer.exchange.handshake.HandshakeActor.HandshakeSuccess
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.protocol.messages.handshake._

class HappyPathHandshakeActorTest extends DefaultHandshakeActorTest("happy-path") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 1 minute,
    refundSignatureAbortTimeout = 1 minute
  )

  "Handshake happy path" should "send peer handshake when started" in {
    shouldForwardPeerHandshake()
  }

  it should "request refund transaction signature after getting counterpart peer handshake" in {
    givenCounterpartPeerHandshake()
    shouldCreateDeposits()
    shouldForwardRefundSignatureRequest()
    blockchain.expectMsg(WatchMultisigKeys(handshake.exchange.requiredSignatures))
  }

  it should "reject signature of invalid counterpart refund transactions" in {
    val invalidRequest =
      RefundSignatureRequest(exchange.id, ImmutableTransaction(handshake.invalidRefundTransaction))
    gateway.relayMessage(invalidRequest, counterpartId)
    gateway.expectNoMsg(idleTime)
  }

  it should "sign counterpart refund while waiting for our refund" in {
    givenCounterpartSignatureRequest()
    shouldSignCounterpartRefund()
  }

  it should "don't be fooled by invalid refund TX or source" in {
    val invalidReply = RefundSignatureResponse(exchange.id, MockExchangeProtocol.InvalidSignature)
    gateway.relayMessage(invalidReply, counterpartId)
  }

  it should "send commitment TX to the broker after getting his refund TX signed" in {
    givenValidRefundSignatureResponse()
    shouldForwardCommitmentToBroker()
  }

  it should "sign counterpart refund after having our refund signed" in {
    givenCounterpartSignatureRequest()
    shouldSignCounterpartRefund()
  }

  it should "wait until the broker publishes commitments" in {
    listener.expectNoMsg(idleTime)
    givenCommitmentPublicationNotification()
    shouldAckCommitmentNotification()
    val confirmations = protocolConstants.commitmentConfirmations
    blockchain.expectMsgAllOf(
      WatchTransactionConfirmation(handshake.myDeposit.get.getHash, confirmations),
      WatchTransactionConfirmation(handshake.counterpartCommitmentTransaction.getHash, confirmations)
    )
  }

  it should "acknowledge again if the broker keeps notifying the commitment notification" in {
    givenCommitmentPublicationNotification()
    shouldAckCommitmentNotification()
  }

  it should "wait until commitments are confirmed" in {
    listener.expectNoMsg(idleTime)
    val expectedCommitments = Both(
      buyer = handshake.myDeposit,
      seller = ImmutableTransaction(handshake.counterpartCommitmentTransaction)
    )
    for (tx <- expectedCommitments.toSeq) {
      blockchain.send(actor, TransactionConfirmed(tx.get.getHash, 1))
    }
    expectedCommitments.foreach { tx =>
      blockchain.expectMsg(RetrieveTransaction(tx.get.getHash))
      blockchain.reply(TransactionFound(tx.get.getHash, tx))
    }
    listener.expectMsgPF() {
      case HandshakeSuccess(_, `expectedCommitments`, handshake.`mySignedRefund`, _) =>
    }
  }

  it should "finally terminate himself" in {
    listener.expectTerminated(actor)
  }
}
