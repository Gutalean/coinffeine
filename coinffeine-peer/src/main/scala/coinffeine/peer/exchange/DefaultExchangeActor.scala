package coinffeine.peer.exchange

import akka.actor._
import akka.pattern._
import akka.persistence.{RecoveryCompleted, PersistentActor}

import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange._
import coinffeine.model.exchange._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.broadcast.DefaultExchangeTransactionBroadcaster
import coinffeine.peer.exchange.broadcast.ExchangeTransactionBroadcaster._
import coinffeine.peer.exchange.handshake.DefaultHandshakeActor
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.micropayment.{BuyerMicroPaymentChannelActor, MicroPaymentChannelActor, SellerMicroPaymentChannelActor}
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

  private var txBroadcaster: ActorRef = _

  override def preStart(): Unit = {
    log.info("Starting {}", exchange.id)
    super.preStart()
  }

  override def receiveRecover: Receive = {
    case event: RetrievedUserInfo => onRetrievedUserInfo(event)
    case event: ExchangeFinished => onExchangeFinished(event)
    case RecoveryCompleted => self ! ResumeExchange
  }

  override def receiveCommand: Receive = waitingForUserInfo

  private def onRetrievedUserInfo(event: RetrievedUserInfo): Unit = {
    context.actorOf(delegates.handshake(event.user, self), HandshakeActorName)
    context.become(inHandshake(event.user))
  }

  private def onExchangeFinished(event: ExchangeFinished): Unit = {
    collaborators.listener ! event.result
    unblockFunds()
    context.stop(self)
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
      persist(RetrievedUserInfo(user))(onRetrievedUserInfo)

    case Status.Failure(cause) =>
      log.error(cause, "Cannot start handshake of {}", exchange.id)
      finishWith(ExchangeFailure(exchange.cancel(CancellationCause.CannotStartHandshake(cause))))
  }

  private def inHandshake(user: Exchange.PeerInfo): Receive = {
    case HandshakeSuccess(rawExchange, commitments, refundTx)
      if rawExchange.currency == exchange.currency =>
      val handshakingExchange = rawExchange.asInstanceOf[DepositPendingExchange[C]]
      spawnDepositWatcher(handshakingExchange, handshakingExchange.role.select(commitments), refundTx)
      spawnBroadcaster(refundTx)
      val validationResult = exchangeProtocol.validateDeposits(
        commitments, handshakingExchange.amounts, handshakingExchange.requiredSignatures,
        handshakingExchange.parameters.network)
      if (validationResult.forall(_.isSuccess)) {
        startMicropaymentChannel(commitments, handshakingExchange)
      } else {
        startAbortion(
          handshakingExchange.abort(AbortionCause.InvalidCommitments(validationResult), refundTx))
      }

    case HandshakeFailure(cause) =>
      log.error(cause, "Handshake for exchange {} failed!", exchange.id)
      finishWith(
        ExchangeFailure(exchange.cancel(CancellationCause.HandshakeFailed(cause), Some(user))))

    case HandshakeFailureWithCommitment(rawExchange, cause, deposit, refundTx) =>
      spawnDepositWatcher(rawExchange, deposit, refundTx)
      spawnBroadcaster(refundTx)
      startAbortion(
        exchange.abort(AbortionCause.HandshakeWithCommitmentFailed(cause), user, refundTx))

    case update: ExchangeUpdate => collaborators.listener ! update
  }

  private def spawnBroadcaster(refund: ImmutableTransaction): Unit = {
    txBroadcaster =
      context.actorOf(delegates.transactionBroadcaster(refund), TransactionBroadcastActorName)
  }

  private def spawnDepositWatcher(exchange: DepositPendingExchange[_ <: FiatCurrency],
                                  deposit: ImmutableTransaction,
                                  refundTx: ImmutableTransaction): Unit = {
    context.actorOf(delegates.depositWatcher(exchange, deposit, refundTx), "depositWatcher")
  }

  private def startMicropaymentChannel(commitments: Both[ImmutableTransaction],
                                       handshakingExchange: DepositPendingExchange[C]): Unit = {
    val runningExchange = handshakingExchange.startExchanging(commitments)
    val channel = exchangeProtocol.createMicroPaymentChannel(runningExchange)
    val resultListeners = Set(self, txBroadcaster)
    context.actorOf(delegates.micropaymentChannel(channel, resultListeners), ChannelActorName)
    context.become(inMicropaymentChannel(runningExchange))
  }

  private def inMicropaymentChannel(runningExchange: RunningExchange[C]): Receive = {
    case MicroPaymentChannelActor.ChannelSuccess(successTx) =>
      log.info("Finishing exchange '{}' successfully", exchange.id)
      txBroadcaster ! PublishBestTransaction
      context.become(waitingForFinalTransaction(runningExchange, successTx))

    case DepositSpent(broadcastTx, CompletedChannel) =>
      log.info("Finishing exchange '{}' successfully", exchange.id)
      finishWith(ExchangeSuccess(runningExchange.complete))

    case MicroPaymentChannelActor.ChannelFailure(step, cause) =>
      log.error(cause, "Finishing exchange '{}' with a failure in step {}", exchange.id, step)
      txBroadcaster ! PublishBestTransaction
      context.become(failingAtStep(runningExchange, step, cause))

    case DepositSpent(broadcastTx, _) =>
      finishWith(ExchangeFailure(runningExchange.panicked(broadcastTx)))

    case update @ ExchangeUpdate(updatedRunningExchange: RunningExchange[C]) =>
      collaborators.listener ! update
      context.become(inMicropaymentChannel(updatedRunningExchange))
  }

  private def startAbortion(abortingExchange: AbortingExchange[C]): Unit = {
    log.warning("Exchange {}: starting abortion", exchange.id)
    txBroadcaster ! PublishBestTransaction
    context.become(aborting(abortingExchange))
  }

  private def aborting(abortingExchange: AbortingExchange[C]): Receive = {
    case DepositSpent(tx, DepositRefund | ChannelAtStep(_)) =>
      finishWith(ExchangeFailure(abortingExchange.broadcast(tx)))

    case DepositSpent(tx, _) =>
      log.error("When aborting {} and unexpected transaction was broadcast: {}",
        abortingExchange.id, tx)
      finishWith(ExchangeFailure(abortingExchange.broadcast(tx)))

    case FailedBroadcast(cause) =>
      log.error(cause, "Cannot broadcast the refund transaction")
      finishWith(ExchangeFailure(abortingExchange.failedToBroadcast))
  }

  private def failingAtStep(runningExchange: RunningExchange[C],
                            step: Int,
                            stepFailure: Throwable): Receive = {
    case DepositSpent(tx, destination) =>
      val expectedDestination = ChannelAtStep(step)
      if (destination != expectedDestination) {
        log.warning("Expected broadcast of {} but got {} for exchange {} (tx = {})",
          expectedDestination, destination, exchange.id, tx)
      }
      finishWith(ExchangeFailure(runningExchange.stepFailure(step, stepFailure, Some(tx))))

    case FailedBroadcast(cause) =>
      log.error(cause, "Cannot broadcast any recovery transaction")
      finishWith(ExchangeFailure(runningExchange.stepFailure(step, stepFailure, transaction = None)))
  }

  private def waitingForFinalTransaction(runningExchange: RunningExchange[C],
                                         expectedLastTx: Option[ImmutableTransaction]): Receive = {

    case DepositSpent(_, CompletedChannel) =>
      finishWith(ExchangeSuccess(runningExchange.complete))

    case DepositSpent(broadcastTx, destination) =>
      log.error("{} ({}) was unexpectedly broadcast for exchange {}", broadcastTx,
        destination, exchange.id)
      finishWith(ExchangeFailure(runningExchange.unexpectedBroadcast(broadcastTx)))

    case FailedBroadcast(cause) =>
      log.error(cause, "The finishing transaction could not be broadcast")
      finishWith(ExchangeFailure(runningExchange.noBroadcast))
  }

  private def finishWith(result: ExchangeResult): Unit = {
    persist(ExchangeFinished(result))(onExchangeFinished)
  }
}

object DefaultExchangeActor {

  trait Delegates {
    def handshake(user: Exchange.PeerInfo, listener: ActorRef): Props
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
          DefaultExchangeTransactionBroadcaster.props(
            refund,
            DefaultExchangeTransactionBroadcaster.Collaborators(bitcoinPeer, blockchain, context.self),
            protocolConstants)

        def handshake(user: Exchange.PeerInfo, listener: ActorRef) = DefaultHandshakeActor.props(
          DefaultHandshakeActor.ExchangeToStart(exchange, user),
          DefaultHandshakeActor.Collaborators(gateway, blockchain, wallet, listener),
          DefaultHandshakeActor.ProtocolDetails(exchangeProtocol, protocolConstants)
        )

        def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                                resultListeners: Set[ActorRef]): Props = {
          val propsFactory = exchange.role match {
            case BuyerRole => BuyerMicroPaymentChannelActor.props _
            case SellerRole => SellerMicroPaymentChannelActor.props _
          }
          propsFactory(channel, protocolConstants,
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
  private case class RetrievedUserInfo(user: Exchange.PeerInfo) extends PersistentEvent
  private case class ExchangeFinished(result: ExchangeResult) extends PersistentEvent
}
