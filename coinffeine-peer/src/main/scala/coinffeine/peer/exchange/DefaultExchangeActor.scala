package coinffeine.peer.exchange

import akka.actor._

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor.{BlockchainActorReference, RetrieveBlockchainActor, TransactionPublished}
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.TransactionBroadcastActor.{UnexpectedTxBroadcast => _, _}
import coinffeine.peer.exchange.handshake.HandshakeActor
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.StartMicroPaymentChannel
import coinffeine.peer.exchange.micropayment.{BuyerMicroPaymentChannelActor, MicroPaymentChannelActor, SellerMicroPaymentChannelActor}
import coinffeine.peer.exchange.protocol._

class DefaultExchangeActor[C <: FiatCurrency](
    handshakeActorProps: Props,
    channelActorProps: Role => Props,
    transactionBroadcastActorProps: Props,
    exchangeProtocol: ExchangeProtocol,
    constants: ProtocolConstants) extends Actor with ActorLogging {

  val receive: Receive = {
    case init: StartExchange[C] => new InitializedExchange(init, sender()).start()
  }

  private class InitializedExchange(init: StartExchange[C], resultListener: ActorRef) {
    import init._
    private var blockchain: ActorRef = _
    private val txBroadcaster =
      context.actorOf(transactionBroadcastActorProps, TransactionBroadcastActorName)
    private val handshakeActor = context.actorOf(handshakeActorProps, HandshakeActorName)

    def start(): Unit = {
      log.info(s"Starting exchange ${exchange.id}")
      bitcoinPeer ! RetrieveBlockchainActor
      context.become(retrievingBlockchain)
    }

    private val inHandshake: Receive = {

      case HandshakeSuccess(handshakingExchange: HandshakingExchange[C], commitmentTxs, refundTx) =>
        txBroadcaster ! StartBroadcastHandling(refundTx, bitcoinPeer, resultListeners = Set(self))
        // TODO: what if counterpart deposit is not valid?
        val deposits = exchangeProtocol.validateDeposits(commitmentTxs, handshakingExchange).get
        val runningExchange = RunningExchange(deposits, handshakingExchange)
        val props = channelActorProps(runningExchange.role)
        val ref = context.actorOf(props, MicroPaymentChannelActorName)
        ref ! StartMicroPaymentChannel[C](runningExchange, paymentProcessor, messageGateway,
          resultListeners = Set(self))
        txBroadcaster ! SetMicropaymentActor(ref)
        context.become(inMicropaymentChannel)

      case HandshakeFailure(err) => finishWith(ExchangeFailure(err))
    }

    private val retrievingBlockchain: Receive = {
      case BlockchainActorReference(blockchainRef) =>
        blockchain = blockchainRef
        startHandshake()
        context.become(inHandshake)
    }

    private def startHandshake(): Unit = {
      handshakeActor ! StartHandshake(
        exchange, role, user, messageGateway, blockchain, wallet, listener = self)
    }

    private def finishingExchange(
        result: ExchangeResult, expectedFinishingTx: Option[ImmutableTransaction]): Receive = {
      case ExchangeFinished(TransactionPublished(originalTx, broadcastTx))
          if expectedFinishingTx.exists(_ != originalTx) =>
        val err = UnexpectedTxBroadcast(originalTx, expectedFinishingTx.get)
        log.error(err, "The transaction broadcast for this exchange is different from the one " +
          "that was being expected.")
        log.error("The previous exchange result is going to be overridden by this unexpected error.")
        log.error(s"Previous result: $result")
        finishWith(ExchangeFailure(err))
      case ExchangeFinished(_) =>
        finishWith(result)
      case ExchangeFinishFailure(err) =>
        log.error(err, "The finishing transaction could not be broadcast")
        log.error("The previous exchange result is going to be overridden by this unexpected error.")
        log.error(s"Previous result: $result")
        finishWith(ExchangeFailure(TxBroadcastFailed(err)))
    }

    private def inMicropaymentChannel: Receive = {

      case MicroPaymentChannelActor.ExchangeSuccess(successTx) =>
        log.info(s"Finishing exchange '${exchange.id}' successfully")
        txBroadcaster ! FinishExchange
        val result = ExchangeSuccess(CompletedExchange.fromExchange(exchange))
        context.become(finishingExchange(result, successTx))

      case MicroPaymentChannelActor.ExchangeFailure(e) =>
        log.warning(s"Finishing exchange '${exchange.id}' with a failure due to ${e.toString}")
        txBroadcaster ! FinishExchange
        context.become(finishingExchange(ExchangeFailure(e), None))

      case progress: ExchangeProgress =>
        resultListener ! progress

      case ExchangeFinished(TransactionPublished(_, broadcastTx)) =>
        finishWith(ExchangeFailure(RiskOfValidRefund(broadcastTx)))
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
      HandshakeActor.props(exchangeProtocol, protocolConstants),
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
