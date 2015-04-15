package coinffeine.peer.exchange.handshake

import scala.concurrent.Future
import scala.util.Try

import akka.actor._
import akka.pattern._
import akka.persistence.{RecoveryCompleted, PersistentActor}
import org.joda.time.DateTime

import coinffeine.common.akka.AskPattern
import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.Both
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
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageForwarder
import coinffeine.protocol.gateway.MessageForwarder.RetrySettings
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.arbitration.{CommitmentNotification, CommitmentNotificationAck}
import coinffeine.protocol.messages.handshake._

private class DefaultHandshakeActor[C <: FiatCurrency](
    exchange: DefaultHandshakeActor.ExchangeToStart[C],
    collaborators: DefaultHandshakeActor.Collaborators,
    protocol: DefaultHandshakeActor.ProtocolDetails) extends PersistentActor with ActorLogging {

  import DefaultHandshakeActor._
  import protocol.constants._
  import context.dispatcher

  override val persistenceId = s"handshake/${exchange.info.id.value}"
  private var timers = Seq.empty[Cancellable]
  private val forwarding = MessageForwarder.Factory(collaborators.gateway)
  private val counterpartRefundSigner =
    context.actorOf(CounterpartRefundSigner.props(collaborators.gateway, exchange.info))

  private var handshake: Handshake[C] = _
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
    forwarding.forward(
      msg = PeerHandshake(exchange.info.id, exchange.user.bitcoinKey.publicKey,
        exchange.user.paymentProcessorAccount),
      destination = exchange.info.counterpartId,
      retry = RetrySettings.continuouslyEvery(protocol.constants.resubmitHandshakeMessagesTimeout)
    ) {
      case RefundSignatureRequest(id, _) if exchange.info.id == id =>
    }
  }

  override def receiveRecover: Receive = {
    case event: HandshakeStarted[C] => onHandshakeStarted(event)
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
      val counterpart = Exchange.PeerInfo(paymentProcessorAccount, publicKey)
      val handshakingExchange =
        exchange.info.handshake(exchange.user, counterpart, exchange.timestamp)
      collaborators.listener ! ExchangeUpdate(handshakingExchange)
      createDeposit(handshakingExchange)
        .map(deposit => protocol.factory.createHandshake(handshakingExchange, deposit))
        .pipeTo(self)

    case createdHandshake: Handshake[C] =>
      persist(HandshakeStarted(createdHandshake))(onHandshakeStarted)

    case Status.Failure(cause) => finishWith(HandshakeFailure(cause, DateTime.now()))
  }

  private def onHandshakeStarted(event: HandshakeStarted[C]): Unit = {
    handshake = event.handshake
    collaborators.blockchain ! BlockchainActor.WatchMultisigKeys(
      event.handshake.exchange.requiredSignatures)
    counterpartRefundSigner ! CounterpartRefundSigner.StartSigningRefunds(handshake)
    context.become(waitForRefundSignature)
  }

  private def onRefundCreated(event: RefundCreated): Unit = {
    refund = event.refund
    log.info("Handshake {}: Got a valid refund TX signature", exchange.info.id)
    context.become(waitForPublication)
  }

  private def onNotifiedCommitments(event: NotifiedCommitments): Unit = {
    context.stop(counterpartRefundSigner)
    context.become(waitForConfirmations(event.commitments))
  }

  private def onFinishedWith(event: FinishedWith): Unit = {
    collaborators.listener ! event.result
    context.stop(self)
  }

  private def createDeposit(exchange: DepositPendingExchange[C]): Future[ImmutableTransaction] = {
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
      forwarding.forward(
        msg = RefundSignatureRequest(exchange.info.id, handshake.myUnsignedRefund),
        destination = exchange.info.counterpartId,
        retry = RetrySettings.continuouslyEvery(protocol.constants.resubmitHandshakeMessagesTimeout)
      ) {
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
        persist(RefundCreated(signedRefund))(onRefundCreated)
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
        persist(NotifiedCommitments(bothCommitments)){ event =>
          onNotifiedCommitments(event)
          acknowledgeCommitmentNotification()
        }
    }

    getNotifiedByBroker orElse abortOnBrokerNotification
  }

  private def forwardMyCommitment(): Unit = {
    forwarding.forward(
      msg = ExchangeCommitment(exchange.info.id, exchange.user.bitcoinKey.publicKey, handshake.myDeposit),
      destination = BrokerId,
      retry = RetrySettings.continuouslyEvery(protocol.constants.resubmitHandshakeMessagesTimeout)
    ) {
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

      case TransactionRejected(tx) =>
        log.debug("Handshake {}: {} was rejected", exchange.info.id, tx)
        val isOwn = tx == handshake.myDeposit.get.getHash
        val cause = CommitmentTransactionRejectedException(exchange.info.id, tx, isOwn)
        log.error("Handshake {}: {}", exchange.info.id, cause.getMessage)
        finishWith(HandshakeFailureWithCommitment(
          handshake.exchange, cause, handshake.myDeposit, refund, DateTime.now()))

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
      val cause = RefundSignatureTimeoutException(exchange.info.id)
      collaborators.gateway ! ForwardMessage(
        ExchangeRejection(exchange.info.id, cause.toString), BrokerId)
      finishWith(HandshakeFailure(cause, DateTime.now()))
  }

  private val abortOnBrokerNotification: Receive = {
    case ReceiveMessage(ExchangeAborted(_, reason), _) =>
      log.info("Handshake {}: Aborted by the broker: {}", exchange.info.id, reason)
      finishWith(HandshakeFailure(HandshakeAbortedException(exchange.info.id, reason), DateTime.now()))
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

object DefaultHandshakeActor {

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

  case class ExchangeToStart[C <: FiatCurrency](info: HandshakingExchange[C],
                                                timestamp: DateTime,
                                                user: Exchange.PeerInfo)

  def props(exchange: ExchangeToStart[_ <: FiatCurrency],
            collaborators: Collaborators,
            protocol: ProtocolDetails) =
    Props(new DefaultHandshakeActor(exchange, collaborators, protocol))

  private case object ResumeHandshake
  private case class HandshakeStarted[C <: FiatCurrency](handshake: Handshake[C]) extends PersistentEvent
  private case class RefundCreated(refund: ImmutableTransaction) extends PersistentEvent
  private case class NotifiedCommitments(commitments: Both[Hash]) extends PersistentEvent
  private case class FinishedWith(result: HandshakeResult) extends PersistentEvent
}
