package coinffeine.peer.exchange

import akka.actor._

import coinffeine.common.akka.ServiceRegistry
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor.{BlockchainActorRef, RetrieveBlockchainActor, TransactionPublished}
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.TransactionBroadcastActor.{UnexpectedTxBroadcast => _, _}
import coinffeine.peer.exchange.handshake.HandshakeActor
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.StartMicroPaymentChannel
import coinffeine.peer.exchange.micropayment.{BuyerMicroPaymentChannelActor, MicroPaymentChannelActor, SellerMicroPaymentChannelActor}
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageGateway

class DefaultExchangeActor(
    handshakeActorProps: (HandshakeActor.ExchangeToHandshake[_ <: FiatCurrency],
                          HandshakeActor.Collaborators) => Props,
    channelActorProps: Role => Props,
    transactionBroadcastActorProps: Props,
    exchangeProtocol: ExchangeProtocol,
    constants: ProtocolConstants) extends Actor with ActorLogging {

  val receive: Receive = {
    case init @ StartExchange(_, _, _, _, _, _) => new InitializedExchange(init, sender()).start()
  }

  private class InitializedExchange[C <: FiatCurrency](init: StartExchange[C],
                                                       resultListener: ActorRef) {
    import init._
    private var blockchain: ActorRef = _
    private val txBroadcaster =
      context.actorOf(transactionBroadcastActorProps, TransactionBroadcastActorName)
    private var handshakeActor: ActorRef = _

    def start(): Unit = {
      log.info(s"Starting exchange ${exchange.id}")
      bitcoinPeer ! RetrieveBlockchainActor
      context.become(retrievingBlockchain)
    }

    private def retrievingBlockchain: Receive = {
      case BlockchainActorRef(blockchainRef) =>
        blockchain = blockchainRef
        startHandshake()
        context.become(inHandshake)
    }

    private def inHandshake: Receive = {

      case HandshakeSuccess(rawExchange, commitments, refundTx)
          if rawExchange.currency == exchange.currency =>
        val handshakingExchange = rawExchange.asInstanceOf[HandshakingExchange[C]]
        txBroadcaster ! StartBroadcastHandling(refundTx, bitcoinPeer, resultListeners = Set(self))
        val validationResult = exchangeProtocol.validateDeposits(
          commitments, handshakingExchange.amounts, handshakingExchange.requiredSignatures)
        if(validationResult.forall(_.isSuccess)) {
          startMicropaymentChannel(commitments, handshakingExchange)
        } else {
          txBroadcaster ! FinishExchange
          context.become(finishingExchange(ExchangeFailure(InvalidCommitments(validationResult))))
        }

      case HandshakeFailure(err) => finishWith(ExchangeFailure(err))
    }

    private def startMicropaymentChannel(commitments: Both[ImmutableTransaction],
                                         handshakingExchange: HandshakingExchange[C]): Unit = {
      val runningExchange = handshakingExchange.startExchanging(commitments)
      val props = channelActorProps(runningExchange.role)
      val ref = context.actorOf(props, MicroPaymentChannelActorName)
      ref ! StartMicroPaymentChannel(runningExchange, paymentProcessor, registry,
        resultListeners = Set(self, txBroadcaster))
      context.become(inMicropaymentChannel(runningExchange))
    }

    private def startHandshake(): Unit = {
      import context.dispatcher
      val exchangeToHandshake = HandshakeActor.ExchangeToHandshake(exchange, user)
      val collaborators = HandshakeActor.Collaborators(
        gateway = new ServiceRegistry(registry).eventuallyLocate(MessageGateway.ServiceId),
        blockchain,
        wallet,
        listener = self
      )
      handshakeActor = context.actorOf(
        handshakeActorProps(exchangeToHandshake, collaborators), HandshakeActorName)
    }

    private def inMicropaymentChannel(runningExchange: RunningExchange[C]): Receive = {

      case MicroPaymentChannelActor.ExchangeSuccess(successTx) =>
        log.info(s"Finishing exchange '${exchange.id}' successfully")
        txBroadcaster ! FinishExchange
        val result = ExchangeSuccess(runningExchange.complete)
        context.become(finishingExchange(result, successTx))

      case MicroPaymentChannelActor.ExchangeFailure(cause) =>
        log.error(cause,
          s"Finishing exchange '${exchange.id}' with a failure due to ${cause.toString}")
        txBroadcaster ! FinishExchange
        context.become(finishingExchange(ExchangeFailure(cause)))

      case progress: ExchangeProgress =>
        resultListener ! progress

      case ExchangeFinished(TransactionPublished(_, broadcastTx)) =>
        finishWith(ExchangeFailure(RiskOfValidRefund(broadcastTx)))
    }

    private def finishingExchange(result: ExchangeResult,
                                  expectedFinishingTx: Option[ImmutableTransaction] = None): Receive = {
      case ExchangeFinished(TransactionPublished(originalTx, broadcastTx))
          if expectedFinishingTx.exists(_ != originalTx) =>
        val err = UnexpectedTxBroadcast(originalTx, expectedFinishingTx.get)
        log.error(err, "The transaction broadcast for this exchange is different from the one " +
          "that was being expected.")
        log.error("The previous exchange result is going to be overridden by this unexpected error.")
        log.error(s"Previous result: $result")
        finishWith(ExchangeFailure(err))

      case ExchangeFinished(_) => finishWith(result)

      case ExchangeFinishFailure(err) =>
        log.error(err, "The finishing transaction could not be broadcast")
        log.error("The previous exchange result is going to be overridden by this unexpected error.")
        log.error(s"Previous result: $result")
        finishWith(ExchangeFailure(TxBroadcastFailed(err)))
    }

    private def finishWith(result: Any): Unit = {
      resultListener ! result
      context.stop(self)
    }
  }
}

object DefaultExchangeActor {

  trait Component extends ExchangeActor.Component {
    this: ExchangeProtocol.Component with ProtocolConstants.Component =>

    override lazy val exchangeActorProps = Props(new DefaultExchangeActor(
      (exchange, collaborators) => HandshakeActor.props(exchange, collaborators,
        HandshakeActor.ProtocolDetails(exchangeProtocol, protocolConstants)),
      channelActorProps = {
        case BuyerRole => BuyerMicroPaymentChannelActor.props(exchangeProtocol, protocolConstants)
        case SellerRole => SellerMicroPaymentChannelActor.props(exchangeProtocol, protocolConstants)
      },
      TransactionBroadcastActor.props(protocolConstants),
      exchangeProtocol,
      protocolConstants
    ))
  }
}
