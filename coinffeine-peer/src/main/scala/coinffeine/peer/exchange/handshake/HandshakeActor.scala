package coinffeine.peer.exchange.handshake

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.pattern._

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin.{Hash, ImmutableTransaction}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.network.BrokerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BlockchainActor._
import coinffeine.peer.bitcoin.WalletActor.{DepositCreated, DepositCreationError}
import coinffeine.peer.bitcoin.{BlockchainActor, WalletActor}
import coinffeine.peer.exchange.protocol.Handshake.InvalidRefundSignature
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.arbitration.CommitmentNotification
import coinffeine.protocol.messages.handshake._

private class HandshakeActor[C <: FiatCurrency](
    exchangeProtocol: ExchangeProtocol,
    collaborators: HandshakeActor.Collaborators,
    constants: ProtocolConstants) extends Actor with ActorLogging {
  import coinffeine.peer.exchange.handshake.HandshakeActor._

  private var timers = Seq.empty[Cancellable]

  override def postStop(): Unit = {
    timers.foreach(_.cancel())
  }

  override def receive = {
    case init: StartHandshake[C] => new InitializedHandshake(init).startHandshake()
  }

  private class InitializedHandshake(init: StartHandshake[C]) {
    import constants._
    import context.dispatcher
    import init._

    private val counterpartRefundSigner =
      context.actorOf(CounterpartRefundSigner.props(collaborators.gateway, exchange))

    def startHandshake(): Unit = {
      subscribeToMessages()
      handshakePeer()
      scheduleTimeouts()
      log.info("Handshake {}: Handshake started", exchange.id)
      context.become(waitForPeerHandshake)
    }

    private def receivePeerHandshake: Receive = {
      case ReceiveMessage(PeerHandshake(_, publicKey, paymentProcessorAccount), _) =>
        val counterpart = Exchange.PeerInfo(paymentProcessorAccount, publicKey)
        val handshakingExchange = exchange.startHandshaking(user, counterpart)
        createDeposit(handshakingExchange)
          .map(deposit => exchangeProtocol.createHandshake(handshakingExchange, deposit))
          .pipeTo(self)

      case handshake: Handshake[C] =>
        collaborators.blockchain ! BlockchainActor.WatchMultisigKeys(
          handshake.exchange.requiredSignatures.toSeq)
        requestRefundSignature(handshake)
        counterpartRefundSigner ! CounterpartRefundSigner.StartSigningRefunds(handshake)
        context.become(waitForRefundSignature(handshake))

      case Status.Failure(cause) =>
        finishWithResult(Failure(cause))

      case ResubmitRequest => handshakePeer()
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

    private def receiveRefundSignature(handshake: Handshake[C]): Receive = {
      case ReceiveMessage(RefundSignatureResponse(_, herSignature), _) =>
        try {
          val signedRefund = handshake.signMyRefund(herSignature)
          collaborators.gateway ! ForwardMessage(
            ExchangeCommitment(exchange.id, handshake.myDeposit), BrokerId)
          log.info("Handshake {}: Got a valid refund TX signature", exchange.id)
          context.become(waitForPublication(handshake, signedRefund))
        } catch {
          case cause: InvalidRefundSignature =>
            requestRefundSignature(handshake)
            log.warning("Handshake {}: Rejecting invalid refund signature: {}", exchange.id, cause)
        }

      case ResubmitRequest =>
        requestRefundSignature(handshake)
        log.info("Handshake {}: Re-requesting refund signature", exchange.id)
    }

    private val abortOnSignatureTimeout: Receive = {
      case RequestSignatureTimeout =>
        val cause = RefundSignatureTimeoutException(exchange.id)
        collaborators.gateway ! ForwardMessage(
          ExchangeRejection(exchange.id, cause.toString), BrokerId)
        finishWithResult(Failure(cause))
    }

    private def getNotifiedByBroker(handshake: Handshake[C],
                                    refund: ImmutableTransaction): Receive = {
      case ReceiveMessage(CommitmentNotification(_, bothCommitments), _) =>
        bothCommitments.toSeq.foreach { tx =>
          collaborators.blockchain ! WatchTransactionConfirmation(tx, commitmentConfirmations)
        }
        context.stop(counterpartRefundSigner)
        log.info("Handshake {}: The broker published {}, waiting for confirmations",
          exchange.id, bothCommitments)
        context.become(waitForConfirmations(handshake, bothCommitments, refund))
    }

    private val abortOnBrokerNotification: Receive = {
      case ReceiveMessage(ExchangeAborted(_, reason), _) =>
        log.info("Handshake {}: Aborted by the broker: {}", exchange.id, reason)
        finishWithResult(Failure(HandshakeAbortedException(exchange.id, reason)))
    }

    private val waitForPeerHandshake: Receive =
      receivePeerHandshake orElse abortOnSignatureTimeout orElse abortOnBrokerNotification

    private def waitForRefundSignature(handshake: Handshake[C]) =
      receiveRefundSignature(handshake) orElse abortOnSignatureTimeout orElse
        abortOnBrokerNotification

    private def waitForPublication(handshake: Handshake[C], signedRefund: ImmutableTransaction) =
      getNotifiedByBroker(handshake, signedRefund) orElse abortOnBrokerNotification

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
          val cause = CommitmentTransactionRejectedException(exchange.id, tx, isOwn)
          log.error("Handshake {}: {}", exchange.id, cause.getMessage)
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

    private def subscribeToMessages(): Unit = {
      val id = exchange.id
      val counterpart = exchange.counterpartId
      collaborators.gateway ! Subscribe {
        case ReceiveMessage(CommitmentNotification(`id`, _) | ExchangeAborted(`id`, _), BrokerId) =>
        case ReceiveMessage(PeerHandshake(`id`, _, _) |
                            RefundSignatureResponse(`id`, _), `counterpart`) =>
      }
    }

    private def scheduleTimeouts(): Unit = {
      timers = Seq(
        context.system.scheduler.schedule(
          initialDelay = resubmitHandshakeMessagesTimeout,
          interval = resubmitHandshakeMessagesTimeout,
          receiver = self,
          message = ResubmitRequest
        ),
        context.system.scheduler.scheduleOnce(
          delay = refundSignatureAbortTimeout,
          receiver = self,
          message = RequestSignatureTimeout
        )
      )
    }

    private def handshakePeer(): Unit = {
      val handshake = PeerHandshake(exchange.id, user.bitcoinKey.publicKey, user.paymentProcessorAccount)
      collaborators.gateway ! ForwardMessage(handshake, exchange.counterpartId)
    }

    private def requestRefundSignature(handshake: Handshake[C]): Unit = {
      log.debug("Handshake {}: requesting refund signature", exchange.id)
      collaborators.gateway ! ForwardMessage(
        RefundSignatureRequest(exchange.id, handshake.myUnsignedRefund), exchange.counterpartId)
    }

    private def finishWithResult(result: Try[HandshakeSuccess]): Unit = {
      val message = result match {
        case Success(success) =>
          log.info("Handshake {}: succeeded", exchange.id)
          success
        case Failure(cause) =>
          log.error(cause, "Handshake {}: handshake failed with", exchange.id)
          HandshakeFailure(cause)
      }
      collaborators.listener ! message
      self ! PoisonPill
    }
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

  def props(exchangeProtocol: ExchangeProtocol,
            collaborators: Collaborators,
            constants: ProtocolConstants) =
    Props(new HandshakeActor(exchangeProtocol, collaborators, constants))

  /** Sent to the actor to start the handshake
    *
    * @constructor
    * @param exchange         Exchange to start the handshake for
    * @param user             User key and payment id
    */
  case class StartHandshake[C <: FiatCurrency](exchange: NonStartedExchange[C],
                                               user: Exchange.PeerInfo)

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

  /** Internal message to remind about resubmitting messages. */
  private case object ResubmitRequest
  /** Internal message that aborts the handshake. */
  private case object RequestSignatureTimeout
}
