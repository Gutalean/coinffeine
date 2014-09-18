package coinffeine.peer.exchange

import akka.actor._

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange.{HandshakeFailed, HandshakeWithCommitmentFailed, InvalidCommitments}
import coinffeine.model.exchange._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor.{BlockchainActorRef, RetrieveBlockchainActor, TransactionPublished}
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.TransactionBroadcastActor.{UnexpectedTxBroadcast => _, _}
import coinffeine.peer.exchange.handshake.HandshakeActor
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.micropayment.{BuyerMicroPaymentChannelActor, MicroPaymentChannelActor, SellerMicroPaymentChannelActor}
import coinffeine.peer.exchange.protocol._

class DefaultExchangeActor[C <: FiatCurrency](
    exchangeProtocol: ExchangeProtocol,
    exchange: ExchangeToStart[C],
    delegates: DefaultExchangeActor.Delegates,
    collaborators: ExchangeActor.Collaborators) extends Actor with ActorLogging {

  private var blockchain: ActorRef = _
  private val txBroadcaster =
    context.actorOf(delegates.transactionBroadcaster, TransactionBroadcastActorName)
  private var handshakeActor: ActorRef = _

  override def preStart(): Unit = {
    log.info("Starting exchange {}", exchange.info.id)
    collaborators.bitcoinPeer ! RetrieveBlockchainActor
  }

  val receive: Receive = {
    case BlockchainActorRef(blockchainRef) =>
      blockchain = blockchainRef
      startHandshake()
      context.become(inHandshake)
  }

  private def inHandshake: Receive = {
    case HandshakeSuccess(rawExchange, commitments, refundTx)
      if rawExchange.currency == exchange.info.currency =>
      val handshakingExchange = rawExchange.asInstanceOf[HandshakingExchange[C]]
      val validationResult = exchangeProtocol.validateDeposits(
        commitments, handshakingExchange.amounts, handshakingExchange.requiredSignatures)
      if (validationResult.forall(_.isSuccess)) {
        spawnBroadcaster(refundTx)
        startMicropaymentChannel(commitments, handshakingExchange)
      } else {
        startAbortion(handshakingExchange.abort(InvalidCommitments(validationResult), refundTx),
          refundTx)
      }

    case HandshakeFailure(cause, None) =>
      finishWith(ExchangeFailure(exchange.info.cancel(HandshakeFailed(cause), Some(exchange.user))))

    case HandshakeFailure(cause, Some(refundTx)) =>
      startAbortion(
        exchange.info.abort(HandshakeWithCommitmentFailed(cause), exchange.user, refundTx), refundTx)
  }

  private def spawnBroadcaster(refundTx: ImmutableTransaction): Unit = {
    txBroadcaster ! StartBroadcastHandling(refundTx, collaborators.bitcoinPeer,
      resultListeners = Set(self))
  }

  private def startMicropaymentChannel(commitments: Both[ImmutableTransaction],
                                       handshakingExchange: HandshakingExchange[C]): Unit = {
    val runningExchange = handshakingExchange.startExchanging(commitments)
    val channel = exchangeProtocol.createMicroPaymentChannel(runningExchange)
    val resultListeners = Set(self, txBroadcaster)
    context.actorOf(delegates.micropaymentChannel(channel, resultListeners), ChannelActorName)
    context.become(inMicropaymentChannel(runningExchange))
  }

  private def startHandshake(): Unit = {
    val handshakeCollaborators = HandshakeActor.Collaborators(
      collaborators.gateway, blockchain, collaborators.wallet, listener = self)
    handshakeActor = context.actorOf(
      delegates.handshake(exchange, handshakeCollaborators), HandshakeActorName)
  }

  private def inMicropaymentChannel(runningExchange: RunningExchange[C]): Receive = {

    case MicroPaymentChannelActor.ChannelSuccess(successTx) =>
      log.info("Finishing exchange '{}' successfully", exchange.info.id)
      txBroadcaster ! FinishExchange
      context.become(waitingForFinalTransaction(runningExchange, successTx))

    case MicroPaymentChannelActor.ChannelFailure(step, cause) =>
      log.error(cause, "Finishing exchange '{}' with a failure in step {}", exchange.info.id, step)
      txBroadcaster ! FinishExchange
      context.become(failingAtStep(runningExchange, step, cause))

    case update: ExchangeUpdate =>
      collaborators.listener ! update

    case ExchangeFinished(TransactionPublished(_, broadcastTx)) =>
      finishWith(ExchangeFailure(runningExchange.panicked(broadcastTx)))
  }

  private def startAbortion(abortingExchange: AbortingExchange[C],
                            refundTx: ImmutableTransaction): Unit = {
    spawnBroadcaster(refundTx)
    txBroadcaster ! FinishExchange
    context.become(aborting(abortingExchange, refundTx))
  }

  private def aborting(abortingExchange: AbortingExchange[C],
                       refundTx: ImmutableTransaction): Receive = {
    case ExchangeFinished(TransactionPublished(`refundTx`, broadcastTx)) =>
      finishWith(ExchangeFailure(abortingExchange.broadcast(broadcastTx)))

    case ExchangeFinished(TransactionPublished(finalTx, _)) =>
      log.error("Cannot broadcast the refund transaction, broadcast {} instead", finalTx)
      finishWith(ExchangeFailure(abortingExchange.failedToBroadcast))

    case ExchangeFinishFailure(cause) =>
      log.error(cause, "Cannot broadcast the refund transaction")
      finishWith(ExchangeFailure(abortingExchange.failedToBroadcast))
  }

  private def failingAtStep(runningExchange: RunningExchange[C],
                            step: Int,
                            stepFailure: Throwable): Receive = {
    case ExchangeFinished(TransactionPublished(_, broadcastTx)) =>
      finishWith(ExchangeFailure(runningExchange.stepFailure(step, stepFailure, Some(broadcastTx))))

    case ExchangeFinishFailure(cause) =>
      log.error(cause, "Cannot broadcast any recovery transaction")
      finishWith(ExchangeFailure(runningExchange.stepFailure(step, stepFailure, transaction = None)))
  }

  private def waitingForFinalTransaction(runningExchange: RunningExchange[C],
                                         expectedLastTx: Option[ImmutableTransaction]): Receive = {

    case ExchangeFinished(TransactionPublished(originalTx, broadcastTx))
        if expectedLastTx.fold(false)(_ != originalTx) =>
      log.error("{} was broadcast for exchange {} while {} was expected", broadcastTx,
        exchange.info.id, expectedLastTx.get)
      finishWith(ExchangeFailure(runningExchange.unexpectedBroadcast(broadcastTx)))

    case ExchangeFinished(_) => finishWith(ExchangeSuccess(runningExchange.complete))

    case ExchangeFinishFailure(cause) =>
      log.error(cause, "The finishing transaction could not be broadcast")
      finishWith(ExchangeFailure(runningExchange.noBroadcast))
  }

  private def finishWith(result: ExchangeResult): Unit = {
    collaborators.listener ! result
    context.stop(self)
  }
}

object DefaultExchangeActor {

  trait Delegates {
    def handshake(exchange: ExchangeActor.ExchangeToStart[_ <: FiatCurrency],
                  collaborators: HandshakeActor.Collaborators): Props
    def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                            resultListeners: Set[ActorRef]): Props
    def transactionBroadcaster: Props
  }

  trait Component extends ExchangeActor.Component {
    this: ExchangeProtocol.Component with ProtocolConstants.Component =>

    override lazy val exchangeActorProps = (exchange: ExchangeToStart[_ <: FiatCurrency],
                                            collaborators: ExchangeActor.Collaborators) => {
      val delegates = new Delegates {
        val transactionBroadcaster = TransactionBroadcastActor.props(protocolConstants)

        def handshake(exchange: ExchangeToStart[_ <: FiatCurrency],
                      collaborators: HandshakeActor.Collaborators) =
          HandshakeActor.props(exchange, collaborators,
            HandshakeActor.ProtocolDetails(exchangeProtocol, protocolConstants))

        def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                                resultListeners: Set[ActorRef]): Props = {
          val propsFactory = exchange.info.role match {
            case BuyerRole => BuyerMicroPaymentChannelActor.props _
            case SellerRole => SellerMicroPaymentChannelActor.props _
          }
          propsFactory(channel, protocolConstants, MicroPaymentChannelActor.Collaborators(
            collaborators.gateway, collaborators.paymentProcessor, resultListeners))
        }
      }

      Props(new DefaultExchangeActor(exchangeProtocol, exchange, delegates, collaborators))
    }
  }
}
