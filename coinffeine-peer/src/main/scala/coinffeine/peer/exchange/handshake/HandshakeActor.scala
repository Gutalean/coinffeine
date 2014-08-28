package coinffeine.peer.exchange.handshake

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.pattern._

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin.{TransactionSignature, Hash, ImmutableTransaction}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.network.BrokerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BlockchainActor._
import coinffeine.peer.bitcoin.WalletActor.{DepositCreated, DepositCreationError}
import coinffeine.peer.bitcoin.{BlockchainActor, WalletActor}
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageForwarder
import coinffeine.protocol.gateway.MessageForwarder.RetrySettings
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.arbitration.CommitmentNotification
import coinffeine.protocol.messages.handshake._

private class HandshakeActor[C <: FiatCurrency](
    exchange: HandshakeActor.ExchangeToHandshake[C],
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
        createDeposit(handshakingExchange)
          .map(deposit => protocol.factory.createHandshake(handshakingExchange, deposit))
          .pipeTo(self)

      case handshake: Handshake[C] =>
        collaborators.blockchain ! BlockchainActor.WatchMultisigKeys(
          handshake.exchange.requiredSignatures.toSeq)
        counterpartRefundSigner ! CounterpartRefundSigner.StartSigningRefunds(handshake)
        context.become(waitForRefundSignature(handshake))

      case Status.Failure(cause) =>
        finishWithResult(Failure(cause))
    }

    receivePeerHandshake orElse abortOnSignatureTimeout orElse abortOnBrokerNotification
  }

  private def createDeposit(exchange: HandshakingExchange[C]): Future[ImmutableTransaction] = {
    val requiredSignatures = exchange.participants.map(_.bitcoinKey).toSeq
    val depositAmount = exchange.role.select(exchange.amounts.depositTransactionAmounts).output
    AskPattern(
      to = collaborators.wallet,
      request = WalletActor.CreateDeposit(
        exchange.blockedFunds.bitcoin,
        requiredSignatures,
        depositAmount,
        exchange.amounts.transactionFee),
      errorMessage = s"Cannot block $depositAmount in multisig"
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
      msg = ExchangeCommitment(exchange.info.id, handshake.myDeposit),
      destination = BrokerId,
      retry = RetrySettings.continuouslyEvery(protocol.constants.resubmitHandshakeMessagesTimeout)
    ) {
      case commitments: CommitmentNotification => commitments
    }

    val getNotifiedByBroker: Receive = {
      case CommitmentNotification(_, bothCommitments) =>
        bothCommitments.toSeq.foreach { tx =>
          collaborators.blockchain ! WatchTransactionConfirmation(tx, commitmentConfirmations)
        }
        context.stop(counterpartRefundSigner)
        log.info("Handshake {}: The broker published {}, waiting for confirmations",
          exchange.info.id, bothCommitments)
        context.become(waitForConfirmations(handshake, bothCommitments, refund))
    }

    getNotifiedByBroker orElse abortOnBrokerNotification
  }

  private def waitForConfirmations(handshake: Handshake[C],
                                   commitmentIds: Both[Hash],
                                   refund: ImmutableTransaction): Receive = {

    def waitForPendingConfirmations(pendingConfirmation: Set[Hash]): Receive = {
      case TransactionConfirmed(tx, confirmations) if confirmations >= commitmentConfirmations =>
        val stillPending = pendingConfirmation - tx
        if (stillPending.isEmpty) retrieveCommitmentsAndFinish()
        else context.become(waitForPendingConfirmations(stillPending))

      case TransactionRejected(tx) =>
        val isOwn = tx == handshake.myDeposit.get.getHash
        val cause = CommitmentTransactionRejectedException(exchange.info.id, tx, isOwn)
        log.error("Handshake {}: {}", exchange.info.id, cause.getMessage)
        finishWithResult(Failure(cause))
    }

    def retrieveCommitmentsAndFinish(): Unit = {
      retrieveCommitmentTransactions(commitmentIds).onComplete { // FIXME: send self-message
        case Success(commitmentTxs) =>
          finishWithResult(Success(HandshakeSuccess(handshake.exchange, commitmentTxs, refund)))
        case Failure(cause) =>
          finishWithResult(Failure(cause))
      }
    }

    waitForPendingConfirmations(commitmentIds.toSet)
  }

  private def retrieveCommitmentTransactions(
      commitmentIds: Both[Hash]): Future[Both[ImmutableTransaction]] = {
    for {
      buyerTx <- retrieveCommitmentTransaction(commitmentIds.buyer)
      sellerTx <- retrieveCommitmentTransaction(commitmentIds.seller)
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
      finishWithResult(Failure(cause))
  }

  private val abortOnBrokerNotification: Receive = {
    case ReceiveMessage(ExchangeAborted(_, reason), _) =>
      log.info("Handshake {}: Aborted by the broker: {}", exchange.info.id, reason)
      finishWithResult(Failure(HandshakeAbortedException(exchange.info.id, reason)))
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

  private def finishWithResult(result: Try[HandshakeSuccess]): Unit = {
    val message = result match {
      case Success(success) =>
        log.info("Handshake {}: succeeded", exchange.info.id)
        success
      case Failure(cause) =>
        log.error(cause, "Handshake {}: handshake failed with", exchange.info.id)
        HandshakeFailure(cause)
    }
    collaborators.listener ! message
    self ! PoisonPill
  }
}

/** A handshake actor is in charge of entering into a value exchange by getting a refundSignature
  * transaction signed and relying on the broker to publish the commitment TX.
  */
object HandshakeActor {

  /** Details of the exchange to perform the handshake of. */
  case class ExchangeToHandshake[C <: FiatCurrency](info: NonStartedExchange[C],
                                                    user: Exchange.PeerInfo)

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

  def props(exchange: ExchangeToHandshake[_ <: FiatCurrency],
            collaborators: Collaborators,
            protocol: ProtocolDetails) =
    Props(new HandshakeActor(exchange, collaborators, protocol))

  /** Sent to the handshake listeners to notify success with a refundSignature transaction or
    * failure with an exception.
    */
  case class HandshakeSuccess(exchange: HandshakingExchange[_ <: FiatCurrency],
                              bothCommitments: Both[ImmutableTransaction],
                              refundTransaction: ImmutableTransaction)

  case class HandshakeFailure(e: Throwable)

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
