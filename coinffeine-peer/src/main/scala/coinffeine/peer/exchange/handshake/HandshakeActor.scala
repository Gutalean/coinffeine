package coinffeine.peer.exchange.handshake

import scala.concurrent.Future
import scala.util.Try

import akka.actor._
import akka.pattern._
import akka.persistence.{PersistentActor, RecoveryCompleted}
import org.joda.time.DateTime

import coinffeine.common.akka.AskPattern
import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.exchange.HandshakeFailureCause.CannotCreateDeposits
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

/** A handshake actor is in charge of entering into a value exchange by getting a refundSignature
  * transaction signed and relying on the broker to publish the commitment TX.
  */
object HandshakeActor {

  sealed trait HandshakeResult {
    def timestamp: DateTime
  }

  /** Sent to the handshake listeners to notify success. */
  case class HandshakeSuccess(
      exchange: DepositPendingExchange,
      bothCommitments: Both[ImmutableTransaction],
      refundTx: ImmutableTransaction,
      override val timestamp: DateTime) extends HandshakeResult

  /** Sent to the handshake listeners to notify a failure without having committed funds. */
  case class HandshakeFailure(cause: HandshakeFailureCause, override val timestamp: DateTime)
      extends HandshakeResult

  /** Send to listeners to notify a handshake failure after having compromised funds */
  case class HandshakeFailureWithCommitment(
      exchange: DepositPendingExchange,
      cause: Throwable,
      commitment: ImmutableTransaction,
      refundTx: ImmutableTransaction,
      override val timestamp: DateTime) extends HandshakeResult

  case class CommitmentTransactionRejectedException(
      exchangeId: ExchangeId, rejectedTx: Hash, isOwn: Boolean) extends RuntimeException(
    s"Commitment transaction $rejectedTx (${if (isOwn) "ours" else "counterpart"}) was rejected"
  )

  /** A response to a [[HandshakeResult]] indicating the handshake actor must terminate. */
  case object Finish

  /** Compact list of actors that collaborate with the handshake actor.
    *
    * @param gateway     Message gateway for external communication
    * @param blockchain  Blockchain for transaction publication and tracking
    * @param wallet      Wallet actor for deposit creation
    * @param listener    Actor to be notified on handshake result
    */
  case class Collaborators(
      gateway: ActorRef,
      blockchain: ActorRef,
      wallet: ActorRef,
      listener: ActorRef)

  case class ProtocolDetails(factory: ExchangeProtocol, constants: ProtocolConstants)

  case class ExchangeToStart(
      info: HandshakingExchange,
      timestamp: DateTime,
      user: Exchange.PeerInfo)

  def props(
      exchange: ExchangeToStart,
      collaborators: Collaborators,
      protocol: ProtocolDetails) =
    Props(new HandshakeActor(exchange, collaborators, protocol))

  private case object ResumeHandshake

  private case class HandshakeStarted(handshake: Handshake)
      extends PersistentEvent

  private case class RefundCreated(refund: ImmutableTransaction) extends PersistentEvent

  private case class NotifiedCommitments(commitments: Both[Hash]) extends PersistentEvent

  private case class FinishedWith(result: HandshakeResult) extends PersistentEvent

}

private class HandshakeActor(
    exchange: HandshakeActor.ExchangeToStart,
    collaborators: HandshakeActor.Collaborators,
    protocol: HandshakeActor.ProtocolDetails) extends PersistentActor with ActorLogging {

  import context.dispatcher
  import protocol.constants._

  import HandshakeActor._

  override val persistenceId = s"handshake/${exchange.info.id.value}"
  private var timers = Seq.empty[Cancellable]
  private val forwarding = MessageForwarder.Factory(
    collaborators.gateway,
    RetrySettings.continuouslyEvery(protocol.constants.resubmitHandshakeMessagesTimeout))
  private val counterpartRefundSigner =
    context.actorOf(CounterpartRefundSigner.props(collaborators.gateway, exchange.info))

  private var handshake: Handshake = _
  private var refund: ImmutableTransaction = _

  /** Self-message that aborts the handshake. */
  private case object RequestSignatureTimeout

  override def preStart(): Unit = {
    subscribeToMessages()
    scheduleSignatureTimeout()
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    timers.foreach(_.cancel())
  }

  private def sendPeerHandshakeUntilFirstSignatureRequest(): Unit = {
    val peerHandshake = PeerHandshake(
      exchange.info.id,
      exchange.user.bitcoinKey.publicKey,
      exchange.user.paymentProcessorAccount
    )
    forwarding.forward(ForwardMessage(peerHandshake, exchange.info.counterpartId)) {
      case RefundSignatureRequest(id, _) if exchange.info.id == id =>
    }
  }

  override def receiveRecover: Receive = {
    case event: HandshakeStarted => onHandshakeStarted(event)
    case event: RefundCreated => onRefundCreated(event)
    case event: NotifiedCommitments => onNotifiedCommitments(event)
    case event: FinishedWith => onFinishedWith(event)
    case RecoveryCompleted => self ! ResumeHandshake
  }

  override def receiveCommand = abortOnSignatureTimeout orElse abortOnBrokerNotification orElse {

    case ResumeHandshake =>
      log.info("Handshake {}: started", exchange.info.id)
      sendPeerHandshakeUntilFirstSignatureRequest()

    case ReceiveMessage(PeerHandshake(_, publicKey, paymentProcessorAccount), _) =>
      log.debug("Received a handshake request for {}; counterpart using {} and {}",
        exchange.info.id, publicKey, paymentProcessorAccount)
      val counterpart = Exchange.PeerInfo(paymentProcessorAccount, publicKey)
      val handshakingExchange =
        exchange.info.handshake(exchange.user, counterpart, exchange.timestamp)
      collaborators.listener ! ExchangeUpdate(handshakingExchange)
      createDeposit(handshakingExchange)
          .map(deposit => protocol.factory.createHandshake(handshakingExchange, deposit))
          .pipeTo(self)

    case createdHandshake: Handshake =>
      persist(HandshakeStarted(createdHandshake))(onHandshakeStarted)

    case Status.Failure(cause) =>
      log.error(cause, "Handshake {}: cannot create deposits", exchange.info.id)
      finishWith(HandshakeFailure(CannotCreateDeposits, DateTime.now()))
  }

  private def onHandshakeStarted(event: HandshakeStarted): Unit = {
    handshake = event.handshake
    collaborators.blockchain ! BlockchainActor.WatchMultisigKeys(
      event.handshake.exchange.requiredSignatures)
    counterpartRefundSigner ! CounterpartRefundSigner.StartSigningRefunds(handshake)
    context.become(waitForRefundSignature)
  }

  private def onRefundCreated(event: RefundCreated): Unit = {
    refund = event.refund
    context.become(waitForPublication)
  }

  private def onNotifiedCommitments(event: NotifiedCommitments): Unit = {
    context.stop(counterpartRefundSigner)
    context.become(waitForConfirmations(event.commitments))
  }

  private def onFinishedWith(event: FinishedWith): Unit = {
    collaborators.listener ! event.result
    context.become(finishing)
  }

  private def createDeposit(exchange: DepositPendingExchange): Future[ImmutableTransaction] = {
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

  private def waitForRefundSignature: Receive = {

    def validCounterpartSignature(signature: TransactionSignature): Boolean = {
      val signatureAttempt = Try(handshake.signMyRefund(signature))
      if (signatureAttempt.isFailure) {
        log.warning("Handshake {}: discarding invalid counterpart signature: {}",
          exchange.info.id, signature)
      }
      signatureAttempt.isSuccess
    }

    def requestRefundSignature() = {
      forwarding.forward(ForwardMessage(
        RefundSignatureRequest(exchange.info.id, handshake.myUnsignedRefund),
        exchange.info.counterpartId
      )) {
        case RefundSignatureResponse(id, herSignature) if id == exchange.info.id &&
            validCounterpartSignature(herSignature) =>
          handshake.signMyRefund(herSignature)
      }
    }

    if (recoveryFinished) {
      log.debug("Handshake {}: requesting refund signature", exchange.info.id)
      requestRefundSignature()
    }

    val receiveRefundSignature: Receive = {
      case ResumeHandshake =>
        log.info("Handshake {}: resumed after handshake start", exchange.info.id)
        sendPeerHandshakeUntilFirstSignatureRequest()
        requestRefundSignature()

      case signedRefund: ImmutableTransaction =>
        persist(RefundCreated(signedRefund)) { event =>
          log.info("Handshake {}: Got a valid refund TX signature", exchange.info.id)
          onRefundCreated(event)
        }
    }

    receiveRefundSignature orElse abortOnSignatureTimeout orElse abortOnBrokerNotification
  }

  private def waitForPublication: Receive = {
    if (recoveryFinished) {
      forwardMyCommitment()
    }

    val getNotifiedByBroker: Receive = {
      case ResumeHandshake =>
        log.info("Handshake {}: resumed after having my refund signed", exchange.info.id)
        forwardMyCommitment()

      case CommitmentNotification(_, bothCommitments) =>
        persist(NotifiedCommitments(bothCommitments)) { event =>
          onNotifiedCommitments(event)
          acknowledgeCommitmentNotification()
        }
    }

    getNotifiedByBroker orElse abortOnBrokerNotification
  }

  private def forwardMyCommitment(): Unit = {
    val commitment = ExchangeCommitment(
      exchange.info.id, exchange.user.bitcoinKey.publicKey, handshake.myDeposit)
    forwarding.forward(ForwardMessage(commitment, BrokerId)) {
      case commitments: CommitmentNotification if commitments.exchangeId == exchange.info.id =>
        commitments
    }
  }

  private def waitForConfirmations(commitmentIds: Both[Hash]): Receive = {

    def waitForPendingConfirmations(pendingConfirmation: Set[Hash]): Receive = {
      case ResumeHandshake =>
        log.info("Handshake {}: resumed after commitment notification", exchange.info.id)
        acknowledgeCommitmentNotification()
        watchCommitmentConfirmations(commitmentIds)

      case TransactionConfirmed(tx, confirmations) if confirmations >= commitmentConfirmations =>
        log.debug("Handshake {}: {} was confirmed", exchange.info.id, tx)
        val stillPending = pendingConfirmation - tx
        if (stillPending.isEmpty) {
          retrieveCommitmentTransactions(commitmentIds).map { commitmentTxs =>
            HandshakeSuccess(handshake.exchange, commitmentTxs, refund, DateTime.now())
          }.pipeTo(self)
        } else {
          context.become(waitForPendingConfirmations(stillPending))
        }

      case ReceiveMessage(CommitmentNotification(_, bothCommitments), BrokerId) =>
        log.info("Handshake {}: commitment notification was received again; " +
            "seems like last ack was missed, retransmitting it to the broker",
          exchange.info.id)
        acknowledgeCommitmentNotification()

      case result: HandshakeSuccess => finishWith(result)

      case Status.Failure(cause) => finishWith(HandshakeFailureWithCommitment(
        handshake.exchange, cause, handshake.myDeposit, refund, DateTime.now()))
    }

    if (recoveryFinished) {
      watchCommitmentConfirmations(commitmentIds)
    }
    waitForPendingConfirmations(commitmentIds.toSet)
  }

  private def watchCommitmentConfirmations(commitmentIds: Both[Hash]): Unit = {
    log.info("Handshake {}: The broker published {}, waiting for confirmations",
      exchange.info.id, commitmentIds)
    commitmentIds.foreach { txId =>
      collaborators.blockchain ! WatchTransactionConfirmation(txId, commitmentConfirmations)
    }
  }

  private def acknowledgeCommitmentNotification(): Unit = {
    collaborators.gateway ! ForwardMessage(CommitmentNotificationAck(exchange.info.id), BrokerId)
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
      log.error("Handshake {}: timeout waiting for a valid refund signature", exchange.info.id)
      collaborators.gateway ! ForwardMessage(
        ExchangeRejection(exchange.info.id, ExchangeRejection.CounterpartTimeout), BrokerId)
      finishWith(HandshakeFailure(HandshakeFailureCause.SignatureTimeout, DateTime.now()))
  }

  private val abortOnBrokerNotification: Receive = {
    case ReceiveMessage(ExchangeAborted(_, reason), _) =>
      log.error("Handshake {}: aborted by the broker: {}", exchange.info.id, reason.message)
      finishWith(HandshakeFailure(HandshakeFailureCause.BrokerAbortion, DateTime.now()))
  }

  private val finishing: Receive = {
    case HandshakeActor.Finish =>
      log.debug("Finishing by request, deleting journal")
      deleteMessages(Long.MaxValue)
      self ! PoisonPill
  }

  private def subscribeToMessages(): Unit = {
    val id = exchange.info.id
    val counterpart = exchange.info.counterpartId
    collaborators.gateway ! Subscribe {
      case ReceiveMessage(ExchangeAborted(`id`, _) | CommitmentNotification(`id`, _), BrokerId) =>
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
    persist(FinishedWith(result))(onFinishedWith)
  }
}
