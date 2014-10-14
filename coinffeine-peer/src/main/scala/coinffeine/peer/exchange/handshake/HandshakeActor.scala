package coinffeine.peer.exchange.handshake

import scala.concurrent.Future
import scala.util.Try

import akka.actor._
import akka.pattern._

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.network.BrokerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.bitcoin.blockchain.BlockchainActor._
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.bitcoin.wallet.WalletActor.{DepositCreated, DepositCreationError}
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageForwarder
import coinffeine.protocol.gateway.MessageForwarder.RetrySettings
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.arbitration.{CommitmentNotification, CommitmentNotificationAck}
import coinffeine.protocol.messages.handshake._

private class HandshakeActor[C <: FiatCurrency](
    exchange: HandshakeActor.ExchangeToStart[C],
    collaborators: HandshakeActor.Collaborators,
    protocol: HandshakeActor.ProtocolDetails) extends Actor with ActorLogging {

  import HandshakeActor._
  import protocol.constants._
  import context.dispatcher

  private var timers = Seq.empty[Cancellable]
  private val forwarding = MessageForwarder.Factory(collaborators.gateway)
  private val counterpartRefundSigner =
    context.actorOf(CounterpartRefundSigner.props(collaborators.gateway, exchange.info))

  override def preStart(): Unit = {
    subscribeToMessages()
    sendPeerHandshakeUntilFirstSignatureRequest()
    scheduleSignatureTimeout()
    log.info("Handshake {}: Handshake started", exchange.info.id)
  }

  override def postStop(): Unit = {
    timers.foreach(_.cancel())
  }

  private def sendPeerHandshakeUntilFirstSignatureRequest(): Unit = {
    forwarding.forward(
      msg = PeerHandshake(exchange.info.id, exchange.user.bitcoinKey.publicKey,
        exchange.user.paymentProcessorAccount),
      destination = exchange.info.counterpartId,
      retry = RetrySettings.continuouslyEvery(protocol.constants.resubmitHandshakeMessagesTimeout)
    ) {
      case RefundSignatureRequest(exchange.info.`id`, _) =>
    }
  }

  override def receive = waitForPeerHandshake()

  private def waitForPeerHandshake(): Receive = {

    val receivePeerHandshake: Receive = {
      case ReceiveMessage(PeerHandshake(_, publicKey, paymentProcessorAccount), _) =>
        val counterpart = Exchange.PeerInfo(paymentProcessorAccount, publicKey)
        val handshakingExchange = exchange.info.startHandshaking(exchange.user, counterpart)
        collaborators.listener ! ExchangeUpdate(handshakingExchange)
        createDeposit(handshakingExchange)
          .map(deposit => protocol.factory.createHandshake(handshakingExchange, deposit))
          .pipeTo(self)

      case handshake: Handshake[C] =>
        collaborators.blockchain ! BlockchainActor.WatchMultisigKeys(
          handshake.exchange.requiredSignatures.toSeq)
        counterpartRefundSigner ! CounterpartRefundSigner.StartSigningRefunds(handshake)
        context.become(waitForRefundSignature(handshake))

      case Status.Failure(cause) => finishWith(HandshakeFailure(cause))
    }

    receivePeerHandshake orElse abortOnSignatureTimeout orElse abortOnBrokerNotification
  }

  private def createDeposit(exchange: HandshakingExchange[C]): Future[ImmutableTransaction] = {
    val requiredSignatures = exchange.participants.map(_.bitcoinKey)
    val depositAmounts = exchange.role.select(exchange.amounts.deposits)
    AskPattern(
      to = collaborators.wallet,
      request = WalletActor.CreateDeposit(
        exchange.id,
        requiredSignatures,
        depositAmounts.output,
        depositAmounts.fee
      ),
      errorMessage = s"Cannot block ${depositAmounts.output} in multisig"
    ).withImmediateReplyOrError[DepositCreated, DepositCreationError](_.error).map(_.tx)
  }

  private def waitForRefundSignature(handshake: Handshake[C]) = {

    def validCounterpartSignature(signature: TransactionSignature): Boolean = {
      val signatureAttempt = Try(handshake.signMyRefund(signature))
      if (signatureAttempt.isFailure) {
        log.warning("Handshake {}: discarding invalid counterpart signature: {}",
          exchange.info.id, signature)
      }
      signatureAttempt.isSuccess
    }

    log.debug("Handshake {}: requesting refund signature", exchange.info.id)
    forwarding.forward(
      msg = RefundSignatureRequest(exchange.info.id, handshake.myUnsignedRefund),
      destination = exchange.info.counterpartId,
      retry = RetrySettings.continuouslyEvery(protocol.constants.resubmitHandshakeMessagesTimeout)
    ) {
      case RefundSignatureResponse(_, herSignature) if validCounterpartSignature(herSignature) =>
        handshake.signMyRefund(herSignature)
    }

    val receiveRefundSignature: Receive = {
      case signedRefund: ImmutableTransaction =>
        log.info("Handshake {}: Got a valid refund TX signature", exchange.info.id)
        context.become(waitForPublication(handshake, signedRefund))
    }

    receiveRefundSignature orElse abortOnSignatureTimeout orElse abortOnBrokerNotification
  }

  private def waitForPublication(handshake: Handshake[C], refund: ImmutableTransaction) = {

    forwarding.forward(
      msg = ExchangeCommitment(exchange.info.id, exchange.user.bitcoinKey.publicKey, handshake.myDeposit),
      destination = BrokerId,
      retry = RetrySettings.continuouslyEvery(protocol.constants.resubmitHandshakeMessagesTimeout)
    ) {
      case commitments: CommitmentNotification => commitments
    }

    val getNotifiedByBroker: Receive = {
      case CommitmentNotification(_, bothCommitments) =>
        context.stop(counterpartRefundSigner)
        log.info("Handshake {}: The broker published {}, waiting for confirmations",
          exchange.info.id, bothCommitments)
        collaborators.gateway ! ForwardMessage(CommitmentNotificationAck(exchange.info.id), BrokerId)
        context.become(waitForConfirmations(handshake, bothCommitments, refund))
    }

    getNotifiedByBroker orElse abortOnBrokerNotification
  }

  private def waitForConfirmations(handshake: Handshake[C],
                                   commitmentIds: Both[Hash],
                                   refundTx: ImmutableTransaction): Receive = {

    def waitForPendingConfirmations(pendingConfirmation: Set[Hash]): Receive = {
      case TransactionConfirmed(tx, confirmations) if confirmations >= commitmentConfirmations =>
        val stillPending = pendingConfirmation - tx
        if (stillPending.isEmpty) {
          retrieveCommitmentTransactions(commitmentIds).map { commitmentTxs =>
            HandshakeSuccess(handshake.exchange, commitmentTxs, refundTx)
          }.pipeTo(self)
        } else {
          context.become(waitForPendingConfirmations(stillPending))
        }

      case TransactionRejected(tx) =>
        val isOwn = tx == handshake.myDeposit.get.getHash
        val cause = CommitmentTransactionRejectedException(exchange.info.id, tx, isOwn)
        log.error("Handshake {}: {}", exchange.info.id, cause.getMessage)
        finishWith(HandshakeFailureWithCommitment(
          handshake.exchange, cause, handshake.myDeposit, refundTx))

      case CommitmentNotification(_, bothCommitments) =>
        log.info("Handshake {}: commitment notification was received again; " +
          "seems like last ack was missed, retransmitting it to the broker",
          exchange.info.id)
        collaborators.gateway ! ForwardMessage(CommitmentNotificationAck(exchange.info.id), BrokerId)

      case result: HandshakeSuccess => finishWith(result)

      case Status.Failure(cause) => finishWith(HandshakeFailureWithCommitment(
        handshake.exchange, cause, handshake.myDeposit, refundTx))
    }

    commitmentIds.toSeq.foreach { txId =>
      collaborators.blockchain ! WatchTransactionConfirmation(txId, commitmentConfirmations)
    }
    waitForPendingConfirmations(commitmentIds.toSet)
  }

  private def retrieveCommitmentTransactions(
      commitmentIds: Both[Hash]): Future[Both[ImmutableTransaction]] = {
    val retrievals = commitmentIds.map(retrieveCommitmentTransaction)
    for {
      buyerTx <- retrievals.buyer
      sellerTx <- retrievals.seller
    } yield Both(buyer = buyerTx, seller = sellerTx)
  }

  private def retrieveCommitmentTransaction(commitmentId: Hash): Future[ImmutableTransaction] =
    AskPattern(
      to = collaborators.blockchain,
      request = BlockchainActor.RetrieveTransaction(commitmentId),
      errorMessage = s"Cannot retrieve TX $commitmentId"
    ).withImmediateReplyOrError[TransactionFound, TransactionNotFound]().map(_.tx)

  private val abortOnSignatureTimeout: Receive = {
    case RequestSignatureTimeout =>
      val cause = RefundSignatureTimeoutException(exchange.info.id)
      collaborators.gateway ! ForwardMessage(
        ExchangeRejection(exchange.info.id, cause.toString), BrokerId)
      finishWith(HandshakeFailure(cause))
  }

  private val abortOnBrokerNotification: Receive = {
    case ReceiveMessage(ExchangeAborted(_, reason), _) =>
      log.info("Handshake {}: Aborted by the broker: {}", exchange.info.id, reason)
      finishWith(HandshakeFailure(HandshakeAbortedException(exchange.info.id, reason)))
  }

  private def subscribeToMessages(): Unit = {
    val id = exchange.info.id
    val counterpart = exchange.info.counterpartId
    collaborators.gateway ! Subscribe {
      case ReceiveMessage(ExchangeAborted(`id`, _), BrokerId) =>
      case ReceiveMessage(PeerHandshake(`id`, _, _), `counterpart`) =>
    }
  }

  private def scheduleSignatureTimeout(): Unit = {
    timers = Seq(
      context.system.scheduler.scheduleOnce(
        delay = refundSignatureAbortTimeout,
        receiver = self,
        message = RequestSignatureTimeout
      )
    )
  }

  private def finishWith(result: HandshakeResult): Unit = {
    collaborators.listener ! result
    context.stop(self)
  }
}

/** A handshake actor is in charge of entering into a value exchange by getting a refundSignature
  * transaction signed and relying on the broker to publish the commitment TX.
  */
object HandshakeActor {

  /** Compact list of actors that collaborate with the handshake actor.
    *
    * @param gateway     Message gateway for external communication
    * @param blockchain  Blockchain for transaction publication and tracking
    * @param wallet      Wallet actor for deposit creation
    * @param listener    Actor to be notified on handshake result
    */
  case class Collaborators(gateway: ActorRef,
                           blockchain: ActorRef,
                           wallet: ActorRef,
                           listener: ActorRef)

  case class ProtocolDetails(factory: ExchangeProtocol, constants: ProtocolConstants)

  case class ExchangeToStart[C <: FiatCurrency](info: NonStartedExchange[C],
                                                user: Exchange.PeerInfo)

  def props(exchange: ExchangeToStart[_ <: FiatCurrency],
            collaborators: Collaborators,
            protocol: ProtocolDetails) =
    Props(new HandshakeActor(exchange, collaborators, protocol))

  sealed trait HandshakeResult

  /** Sent to the handshake listeners to notify success. */
  case class HandshakeSuccess(exchange: HandshakingExchange[_ <: FiatCurrency],
                              bothCommitments: Both[ImmutableTransaction],
                              refundTransaction: ImmutableTransaction) extends HandshakeResult

  /** Sent to the handshake listeners to notify a failure without having committed funds. */
  case class HandshakeFailure(cause: Throwable) extends HandshakeResult

  /** Send to listeners to notify a handshake failure after having compromised funds */
  case class HandshakeFailureWithCommitment(exchange: HandshakingExchange[_ <: FiatCurrency],
                                            cause: Throwable,
                                            commitment: ImmutableTransaction,
                                            refundTx: ImmutableTransaction) extends HandshakeResult

  case class RefundSignatureTimeoutException(exchangeId: ExchangeId) extends RuntimeException(
    s"Timeout waiting for a valid signature of the refund transaction of handshake $exchangeId")

  case class CommitmentTransactionRejectedException(
       exchangeId: ExchangeId, rejectedTx: Hash, isOwn: Boolean) extends RuntimeException(
    s"Commitment transaction $rejectedTx (${if (isOwn) "ours" else "counterpart"}) was rejected"
  )

  case class HandshakeAbortedException(exchangeId: ExchangeId, reason: String)
    extends RuntimeException(s"Handshake $exchangeId aborted externally: $reason")

  /** Internal message that aborts the handshake. */
  private case object RequestSignatureTimeout
}
