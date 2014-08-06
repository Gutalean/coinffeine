package coinffeine.peer.exchange.handshake

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.actor._

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin.{Hash, ImmutableTransaction}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BlockchainActor.{TransactionConfirmed, TransactionRejected, WatchTransactionConfirmation}
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.bitcoin.WalletActor.{BlockFundsResult, FundsBlocked, FundsBlockingError}
import coinffeine.peer.exchange.protocol.Handshake.{InvalidRefundSignature, InvalidRefundTransaction}
import coinffeine.peer.exchange.protocol._
import coinffeine.peer.exchange.util.MessageForwarding
import coinffeine.protocol.gateway.MessageGateway.{ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.arbitration.CommitmentNotification
import coinffeine.protocol.messages.handshake._

private class HandshakeActor[C <: FiatCurrency](
    exchangeProtocol: ExchangeProtocol, constants: ProtocolConstants) extends Actor with ActorLogging {
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

    private val forwarding = new MessageForwarding(messageGateway, exchange, role)

    def startHandshake(): Unit = {
      subscribeToMessages()
      handshakePeer()
      scheduleTimeouts()
      log.info("Handshake {}: Handshake started", exchange.id)
      context.become(waitForPeerHandshake)
    }

    private def signCounterpartRefund(handshake: Handshake[C]): Receive = {
      case ReceiveMessage(RefundSignatureRequest(_, refundTransaction), _) =>
        try {
          val refundSignature = handshake.signHerRefund(refundTransaction)
          forwarding.forwardToCounterpart(RefundSignatureResponse(exchange.id, refundSignature))
          log.info("Handshake {}: Signing refund TX {}", exchange.id,
            refundTransaction.get.getHashAsString)
        } catch {
          case cause: InvalidRefundTransaction =>
            log.warning("Handshake {}: Dropping invalid refund: {}", exchange.id, cause)
        }
    }

    private def receivePeerHandshake: Receive = {
      case ReceiveMessage(PeerHandshake(_, publicKey, paymentProcessorAccount), _) =>
        val counterpart = Exchange.PeerInfo(paymentProcessorAccount, publicKey)
        val handshakingExchange = HandshakingExchange(user, counterpart, exchange)
        blockFunds(handshakingExchange).onComplete {
          case Success(deposit) =>
            val handshake = exchangeProtocol.createHandshake(handshakingExchange, deposit)
            requestRefundSignature(handshake)
            context.become(waitForRefundSignature(handshake))
          case Failure(cause) =>
            finishWithResult(Failure(cause))
        }

      case ResubmitRequest => handshakePeer()
    }

    private def blockFunds(exchange: HandshakingExchange[C]): Future[ImmutableTransaction] = {
      val requiredSignatures = exchange.participants.map(_.bitcoinKey).toSeq
      val depositAmount = role.myDepositAmount(exchange.amounts)
      AskPattern(
        to = wallet,
        request = WalletActor.BlockFundsInMultisign(requiredSignatures, depositAmount),
        errorMessage = s"Cannot block $depositAmount in multisig"
      ).withImmediateReply[BlockFundsResult]().flatMap {
        case FundsBlocked(_, tx) => Future.successful(tx)
        case FundsBlockingError(_, error) => Future.failed(error)
      }
    }

    private def receiveRefundSignature(handshake: Handshake[C]): Receive = {
      case ReceiveMessage(RefundSignatureResponse(_, herSignature), _) =>
        try {
          val signedRefund = handshake.signMyRefund(herSignature)
          forwarding.forwardToBroker(ExchangeCommitment(exchange.id, handshake.myDeposit))
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
        forwarding.forwardToBroker(ExchangeRejection(exchange.id, cause.toString))
        finishWithResult(Failure(cause))
    }

    private def getNotifiedByBroker(handshake: Handshake[C],
                                    refund: ImmutableTransaction): Receive = {
      case ReceiveMessage(CommitmentNotification(_, bothCommitments), _) =>
        bothCommitments.toSeq.foreach { tx =>
          blockchain ! WatchTransactionConfirmation(tx, commitmentConfirmations)
        }
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
      receiveRefundSignature(handshake) orElse signCounterpartRefund(handshake) orElse
        abortOnSignatureTimeout orElse abortOnBrokerNotification

    private def waitForPublication(handshake: Handshake[C], signedRefund: ImmutableTransaction) =
      getNotifiedByBroker(handshake, signedRefund) orElse signCounterpartRefund(handshake) orElse
        abortOnBrokerNotification

    private def waitForConfirmations(handshake: Handshake[C],
                                     bothCommitments: Both[Hash],
                                     refund: ImmutableTransaction): Receive = {
      def waitForPendingConfirmations(pendingConfirmation: Set[Hash]): Receive = {
        case TransactionConfirmed(tx, confirmations) if confirmations >= commitmentConfirmations =>
          val stillPending = pendingConfirmation - tx
          if (stillPending.nonEmpty) {
            context.become(waitForPendingConfirmations(stillPending))
          } else {
            finishWithResult(Success(HandshakeSuccess(handshake.exchange, bothCommitments, refund)))
          }

        case TransactionRejected(tx) =>
          val isOwn = tx == handshake.myDeposit.get.getHash
          val cause = CommitmentTransactionRejectedException(exchange.id, tx, isOwn)
          log.error("Handshake {}: {}", exchange.id, cause.getMessage)
          finishWithResult(Failure(cause))
      }
      waitForPendingConfirmations(bothCommitments.toSet)
    }

    private def subscribeToMessages(): Unit = {
      val id = exchange.id
      val counterpart = exchange.counterpartId
      messageGateway ! Subscribe {
        case ReceiveMessage(PeerHandshake(`id`, _, _), `counterpart`) |
             ReceiveMessage(RefundSignatureRequest(`id`, _), `counterpart`) |
             ReceiveMessage(RefundSignatureResponse(`id`, _), `counterpart`) |
             ReceiveMessage(CommitmentNotification(`id`, _), exchange.`brokerId`) |
             ReceiveMessage(ExchangeAborted(`id`, _), exchange.`brokerId`) => true
        case other => false
      }
    }

    private def scheduleTimeouts(): Unit = {
      timers = Seq(
        context.system.scheduler.schedule(
          initialDelay = resubmitRefundSignatureTimeout,
          interval = resubmitRefundSignatureTimeout,
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
      forwarding.forwardToCounterpart(
        PeerHandshake(exchange.id, user.bitcoinKey.publicKey, user.paymentProcessorAccount))
    }

    private def requestRefundSignature(handshake: Handshake[C]): Unit = {
      log.debug("Handshake {}: requesting refund signature", exchange.id)
      forwarding.forwardToCounterpart(RefundSignatureRequest(exchange.id, handshake.myUnsignedRefund))
    }

    private def finishWithResult(result: Try[HandshakeSuccess[C]]): Unit = {
      val message = result match {
        case Success(success) =>
          log.info("Handshake {}: succeeded", exchange.id)
          success
        case Failure(cause) =>
          log.error(cause, "Handshake {}: handshake failed with", exchange.id)
          HandshakeFailure(cause)
      }
      listener ! message
      self ! PoisonPill
    }
  }
}

/** A handshake actor is in charge of entering into a value exchange by getting a refundSignature
  * transaction signed and relying on the broker to publish the commitment TX.
  */
object HandshakeActor {

  def props(exchangeProtocol: ExchangeProtocol, constants: ProtocolConstants) =
    Props(new HandshakeActor(exchangeProtocol, constants))

  /** Sent to the actor to start the handshake
    *
    * @constructor
    * @param exchange         Exchange to start the handshake for
    * @param role             Which role to take
    * @param user             User key and payment id
    * @param messageGateway   Communications gateway
    * @param blockchain       Actor to ask for TX confirmations for
    * @param wallet           Wallet actor
    * @param listener         Actor to be notified of the handshake result
    */
  case class StartHandshake[C <: FiatCurrency](
      exchange: Exchange[C],
      role: Role,
      user: Exchange.PeerInfo,
      messageGateway: ActorRef,
      blockchain: ActorRef,
      wallet: ActorRef,
      listener: ActorRef)

  /** Sent to the handshake listeners to notify success with a refundSignature transaction or
    * failure with an exception.
    */
  case class HandshakeSuccess[C <: FiatCurrency](exchange: HandshakingExchange[C],
                                                 bothCommitments: Both[Hash],
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
