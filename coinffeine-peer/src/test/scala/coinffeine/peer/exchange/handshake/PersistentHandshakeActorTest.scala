package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.exchange.Both
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.blockchain.BlockchainActor._
import coinffeine.peer.exchange.handshake.HandshakeActor.HandshakeSuccess

class PersistentHandshakeActorTest extends DefaultHandshakeActorTest("happy-path") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 1 minute,
    refundSignatureAbortTimeout = 1 minute
  )

  "A handshake" should "persist successful counterpart handshake" in {
    shouldForwardPeerHandshake()
    givenCounterpartPeerHandshake()
    shouldCreateDeposits()
    blockchain.expectMsg(WatchMultisigKeys(handshake.exchange.requiredSignatures.toSeq))
    shouldForwardRefundSignatureRequest()
  }

  it should "remember the handshake after a restart" in {
    restartActor()
    blockchain.expectMsg(WatchMultisigKeys(handshake.exchange.requiredSignatures.toSeq))
    shouldForwardPeerHandshake()
    shouldForwardRefundSignatureRequest()
    shouldSignCounterpartRefund()
  }

  it should "persist valid refund signature received" in {
    givenValidRefundSignatureResponse()
    shouldForwardCommitmentToBroker()
  }

  it should "remember the refund signature after a restart" in {
    restartActor()
    blockchain.expectMsg(WatchMultisigKeys(handshake.exchange.requiredSignatures.toSeq))
    shouldForwardPeerHandshake()
    shouldForwardCommitmentToBroker()
  }

  it should "wait until the broker publishes commitments" in {
    listener.expectNoMsg(100 millis)
    givenCommitmentPublicationNotification()
    shouldAckCommitmentNotification()
    val confirmations = protocolConstants.commitmentConfirmations
    blockchain.expectMsgAllOf(
      WatchTransactionConfirmation(handshake.myDeposit.get.getHash, confirmations),
      WatchTransactionConfirmation(handshake.counterpartCommitmentTransaction.getHash, confirmations)
    )
  }

  it should "wait until commitments are confirmed" in {
    listener.expectNoMsg(100 millis)
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
      case HandshakeSuccess(_, `expectedCommitments`, handshake.`mySignedRefund`) =>
    }
  }

  it should "finally terminate himself" in {
    listener.expectTerminated(actor)
  }
}
