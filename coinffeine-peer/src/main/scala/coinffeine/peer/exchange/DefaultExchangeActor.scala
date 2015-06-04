package coinffeine.peer.exchange

import akka.actor._
import akka.pattern._
import akka.persistence.{PersistentActor, RecoveryCompleted}
import org.joda.time.DateTime

import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange._
import coinffeine.model.exchange._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.broadcast.{PersistentTransactionBroadcaster, TransactionBroadcaster}
import coinffeine.peer.exchange.handshake.{HandshakeActor, DefaultHandshakeActor}
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.micropayment.{PayerActor, BuyerMicroPaymentChannelActor, MicroPaymentChannelActor, SellerMicroPaymentChannelActor}
import coinffeine.peer.exchange.protocol._
import coinffeine.peer.payment.PaymentProcessorActor

class DefaultExchangeActor[C <: FiatCurrency](
    exchangeProtocol: ExchangeProtocol,
    exchange: HandshakingExchange[C],
    peerInfoLookup: PeerInfoLookup,
    delegates: DefaultExchangeActor.Delegates,
    collaborators: ExchangeActor.Collaborators) extends PersistentActor with ActorLogging {

  import DefaultExchangeActor._

  override def persistenceId: String = s"exchange-${exchange.id.value}"

  private var txBroadcasterRef: Option[ActorRef] = None
  private var handshakeRef: Option[ActorRef] = None
  private var paymentChannelRef: Option[ActorRef] = None

  override def preStart(): Unit = {
    log.info("Starting {}", exchange.id)
    super.preStart()
  }

  override def postStop(): Unit = {
    log.info("Stopping {}", exchange.id)
  }

  override def receiveRecover: Receive = {
    case event: RetrievedPeerInfo => onRetrievedPeerInfo(event)
    case event: ExchangeFinished => onExchangeFinished(event)
    case RecoveryCompleted => self ! ResumeExchange
  }

  override def receiveCommand: Receive = waitingForUserInfo

  private def onRetrievedPeerInfo(event: RetrievedPeerInfo): Unit = {
    handshakeRef = Some(
      context.actorOf(delegates.handshake(event.user, event.timestamp, self), HandshakeActorName))
    context.watch(handshakeRef.get)
    context.become(inHandshake(event.user))
  }

  private def onExchangeFinished(event: ExchangeFinished): Unit = {
    collaborators.listener ! event.result
    unblockFunds()
    context.become(waitingForFinishExchange)
  }

  private def unblockFunds(): Unit = {
    log.info("Exchange {}: unblocking funds just in case", exchange.id)
    collaborators.wallet ! WalletActor.UnblockBitcoins(exchange.id)
    if (exchange.role == BuyerRole) {
      collaborators.paymentProcessor ! PaymentProcessorActor.UnblockFunds(exchange.id)
    }
  }

  private val waitingForUserInfo: Receive = {
    case ResumeExchange =>
      import context.dispatcher
      peerInfoLookup.lookup().pipeTo(self)

    case user: PeerInfo =>
      persist(RetrievedPeerInfo(user, DateTime.now()))(onRetrievedPeerInfo)

    case Status.Failure(cause) =>
      log.error(cause, "Cannot start handshake of {}", exchange.id)
      finishWith(ExchangeFailure(exchange.cancel(
        cause = CancellationCause.CannotStartHandshake,
        user = None,
        timestamp = DateTime.now()
      )))
  }

  private def inHandshake(user: Exchange.PeerInfo): Receive = {
    case HandshakeSuccess(rawExchange, commitments, refundTx, committedOn)
      if rawExchange.currency == exchange.currency =>
      val handshakingExchange = rawExchange.asInstanceOf[DepositPendingExchange[C]]
      spawnDepositWatcher(handshakingExchange, handshakingExchange.role.select(commitments), refundTx)
      spawnBroadcaster(refundTx)
      val validationResult = exchangeProtocol.validateDeposits(
        commitments, handshakingExchange.amounts, handshakingExchange.requiredSignatures,
        handshakingExchange.parameters.network)
      if (validationResult.forall(_.isSuccess))
        startMicropaymentChannel(commitments, committedOn, handshakingExchange)
      else {
        log.error("Invalid commitments: {}", validationResult)
        startAbortion(handshakingExchange.abort(
          cause = AbortionCause.InvalidCommitments(validationResult.map(_.isFailure)),
          refundTx = refundTx,
          timestamp = DateTime.now()
        ))
      }

    case HandshakeFailure(cause, failedOn) =>
      finishWith(ExchangeFailure(exchange.cancel(
        cause = CancellationCause.HandshakeFailed(cause),
        user = Some(user),
        timestamp = failedOn
      )))

    case HandshakeFailureWithCommitment(rawExchange, cause, deposit, refundTx, failedOn) =>
      spawnDepositWatcher(rawExchange, deposit, refundTx)
      spawnBroadcaster(refundTx)
      startAbortion(exchange.abort(
        cause = AbortionCause.HandshakeCommitmentsFailure,
        user = user,
        refundTx = refundTx,
        timestamp = failedOn
      ))

    case update: ExchangeUpdate => collaborators.listener ! update
  }

  private def spawnBroadcaster(refund: ImmutableTransaction): Unit = {
    txBroadcasterRef = Some(
      context.actorOf(delegates.transactionBroadcaster(refund), BroadcasterActorName))
    context.watch(txBroadcasterRef.get)
  }

  private def spawnDepositWatcher(exchange: DepositPendingExchange[_ <: FiatCurrency],
                                  deposit: ImmutableTransaction,
                                  refundTx: ImmutableTransaction): Unit = {
    context.actorOf(delegates.depositWatcher(exchange, deposit, refundTx), "depositWatcher")
  }

  private def startMicropaymentChannel(commitments: Both[ImmutableTransaction],
                                       commitmentsConfirmedOn: DateTime,
                                       handshakingExchange: DepositPendingExchange[C]): Unit = {
    val runningExchange = handshakingExchange.startExchanging(commitments, commitmentsConfirmedOn)
    val channel = exchangeProtocol.createMicroPaymentChannel(runningExchange)
    val resultListeners = Set(self, txBroadcasterRef.get)
    paymentChannelRef = Some(
      context.actorOf(delegates.micropaymentChannel(channel, resultListeners), ChannelActorName))
    context.watch(paymentChannelRef.get)
    context.become(inMicropaymentChannel(runningExchange))
  }

  private def inMicropaymentChannel(runningExchange: RunningExchange[C]): Receive = {
    case MicroPaymentChannelActor.ChannelSuccess(successTx) =>
      log.info("Finishing exchange '{}' successfully", exchange.id)
      txBroadcasterRef.get ! TransactionBroadcaster.PublishBestTransaction
      context.become(waitingForFinalTransaction(runningExchange, successTx))

    case DepositSpent(broadcastTx, CompletedChannel) =>
      log.info("Finishing exchange '{}' successfully", exchange.id)
      finishWith(ExchangeSuccess(runningExchange.complete(DateTime.now())))

    case MicroPaymentChannelActor.ChannelFailure(step, cause) =>
      log.error(cause, "Finishing exchange '{}' with a failure in step {}", exchange.id, step)
      txBroadcasterRef.get ! TransactionBroadcaster.PublishBestTransaction
      context.become(failingAtStep(runningExchange, step))

    case DepositSpent(broadcastTx, _) =>
      finishWith(ExchangeFailure(runningExchange.panicked(broadcastTx, DateTime.now())))

    case update @ ExchangeUpdate(updatedRunningExchange: RunningExchange[C]) =>
      collaborators.listener ! update
      context.become(inMicropaymentChannel(updatedRunningExchange))
  }

  private def startAbortion(abortingExchange: AbortingExchange[C]): Unit = {
    log.warning("Exchange {}: starting abortion", exchange.id)
    txBroadcasterRef.get ! TransactionBroadcaster.PublishBestTransaction
    context.become(aborting(abortingExchange))
  }

  private def aborting(abortingExchange: AbortingExchange[C]): Receive = {
    case DepositSpent(tx, DepositRefund | ChannelAtStep(_)) =>
      finishWith(ExchangeFailure(abortingExchange.broadcast(tx, DateTime.now())))

    case DepositSpent(tx, _) =>
      log.error("When aborting {} and unexpected transaction was broadcast: {}",
        abortingExchange.id, tx)
      finishWith(ExchangeFailure(abortingExchange.broadcast(tx, DateTime.now())))

    case TransactionBroadcaster.FailedBroadcast(cause) =>
      log.error(cause, "Cannot broadcast the refund transaction")
      finishWith(ExchangeFailure(abortingExchange.failedToBroadcast(DateTime.now())))
  }

  private def failingAtStep(runningExchange: RunningExchange[C], step: Int): Receive = {
    case DepositSpent(tx, destination) =>
      val expectedDestination = ChannelAtStep(step)
      if (destination != expectedDestination) {
        log.warning("Expected broadcast of {} but got {} for exchange {} (tx = {})",
          expectedDestination, destination, exchange.id, tx)
      }
      finishWith(ExchangeFailure(runningExchange.stepFailure(step, Some(tx), DateTime.now())))

    case TransactionBroadcaster.FailedBroadcast(cause) =>
      log.error(cause, "Cannot broadcast any recovery transaction")
      finishWith(ExchangeFailure(
        runningExchange.stepFailure(step, transaction = None, timestamp = DateTime.now())))
  }

  private def waitingForFinalTransaction(runningExchange: RunningExchange[C],
                                         expectedLastTx: Option[ImmutableTransaction]): Receive = {

    case DepositSpent(_, CompletedChannel) =>
      finishWith(ExchangeSuccess(runningExchange.complete(DateTime.now())))

    case DepositSpent(broadcastTx, destination) =>
      log.error("{} ({}) was unexpectedly broadcast for exchange {}", broadcastTx,
        destination, exchange.id)
      finishWith(ExchangeFailure(runningExchange.unexpectedBroadcast(broadcastTx, DateTime.now())))

    case TransactionBroadcaster.FailedBroadcast(cause) =>
      log.error(cause, "The finishing transaction could not be broadcast")
      finishWith(ExchangeFailure(runningExchange.noBroadcast(DateTime.now())))
  }

  private def finishWith(result: ExchangeResult): Unit = {
    persist(ExchangeFinished(result))(onExchangeFinished)
  }

  private def waitingForFinishExchange: Receive = {
    case ExchangeActor.FinishExchange =>
      log.debug("{}: deleting journal and finishing delegates", exchange.id)
      deleteMessages(lastSequenceNr)
      txBroadcasterRef.foreach(_ ! TransactionBroadcaster.Finish)
      handshakeRef.foreach(_ ! HandshakeActor.Finish)
      paymentChannelRef.foreach(_ ! MicroPaymentChannelActor.Finish)
      stopAfterTerminationOf((txBroadcasterRef ++ handshakeRef ++ paymentChannelRef).toSet)
  }

  private def stopAfterTerminationOf(pendingTerminations: Set[ActorRef]): Unit = {
    if (pendingTerminations.isEmpty) self ! PoisonPill
    else context.become {
      case Terminated(ref) => stopAfterTerminationOf(pendingTerminations - ref)
    }
  }
}

object DefaultExchangeActor {

  trait Delegates {
    def handshake(user: Exchange.PeerInfo, timestamp: DateTime, listener: ActorRef): Props
    def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                            resultListeners: Set[ActorRef]): Props
    def transactionBroadcaster(refund: ImmutableTransaction)(implicit context: ActorContext): Props
    def depositWatcher(exchange: DepositPendingExchange[_ <: FiatCurrency],
                       deposit: ImmutableTransaction,
                       refundTx: ImmutableTransaction)(implicit context: ActorContext): Props
  }

  trait Component extends ExchangeActor.Component {
    this: ExchangeProtocol.Component with ProtocolConstants.Component =>

    override def exchangeActorProps(exchange: HandshakingExchange[_ <: FiatCurrency],
                                    collaborators: ExchangeActor.Collaborators) = {
      import collaborators._

      val delegates = new Delegates {
        def transactionBroadcaster(refund: ImmutableTransaction)(implicit context: ActorContext) =
          PersistentTransactionBroadcaster.props(
            refund,
            PersistentTransactionBroadcaster.Collaborators(bitcoinPeer, blockchain, context.self),
            protocolConstants)

        def handshake(user: Exchange.PeerInfo,
                      timestamp: DateTime,
                      listener: ActorRef) = DefaultHandshakeActor.props(
          DefaultHandshakeActor.ExchangeToStart(exchange, timestamp, user),
          DefaultHandshakeActor.Collaborators(gateway, blockchain, wallet, listener),
          DefaultHandshakeActor.ProtocolDetails(exchangeProtocol, protocolConstants)
        )

        def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                                resultListeners: Set[ActorRef]): Props = exchange.role match {
          case BuyerRole => BuyerMicroPaymentChannelActor.props(channel, protocolConstants,
            MicroPaymentChannelActor.Collaborators(gateway, paymentProcessor, resultListeners),
            new BuyerMicroPaymentChannelActor.Delegates {
              override def payer() = PayerActor.props()
            })
          case SellerRole => SellerMicroPaymentChannelActor.props(channel, protocolConstants,
            MicroPaymentChannelActor.Collaborators(gateway, paymentProcessor, resultListeners))
        }

        def depositWatcher(exchange: DepositPendingExchange[_ <: FiatCurrency],
                           deposit: ImmutableTransaction,
                           refundTx: ImmutableTransaction)(implicit context: ActorContext) =
          Props(new DepositWatcher(exchange, deposit, refundTx,
            DepositWatcher.Collaborators(collaborators.blockchain, context.self)))
      }

      val lookup = new DefaultPeerInfoLookup(wallet, paymentProcessor)

      Props(new DefaultExchangeActor(exchangeProtocol, exchange, lookup, delegates, collaborators))
    }
  }

  private case object ResumeExchange
  private case class RetrievedPeerInfo(user: Exchange.PeerInfo, timestamp: DateTime)
    extends PersistentEvent
  private case class ExchangeFinished(result: ExchangeResult) extends PersistentEvent
}
