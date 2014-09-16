package coinffeine.peer.exchange

import akka.actor._

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency
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
      txBroadcaster ! StartBroadcastHandling(refundTx, collaborators.bitcoinPeer,
        resultListeners = Set(self))
      val validationResult = exchangeProtocol.validateDeposits(
        commitments, handshakingExchange.amounts, handshakingExchange.requiredSignatures)
      if (validationResult.forall(_.isSuccess)) {
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

    case MicroPaymentChannelActor.ExchangeSuccess(successTx) =>
      log.info("Finishing exchange '{}' successfully", exchange.info.id)
      txBroadcaster ! FinishExchange
      val result = ExchangeSuccess(runningExchange.complete)
      context.become(finishingExchange(result, successTx))

    case MicroPaymentChannelActor.ExchangeFailure(cause) =>
      log.error(cause, "Finishing exchange '{}' with a failure", exchange.info.id)
      txBroadcaster ! FinishExchange
      context.become(finishingExchange(ExchangeFailure(cause)))

    case progress: ExchangeProgress =>
      collaborators.resultListener ! progress

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
      log.error("Previous result: {}", result)
      finishWith(ExchangeFailure(err))

    case ExchangeFinished(_) => finishWith(result)

    case ExchangeFinishFailure(err) =>
      log.error(err, "The finishing transaction could not be broadcast")
      log.error("The previous exchange result is going to be overridden by this unexpected error.")
      log.error("Previous result: {}", result)
      finishWith(ExchangeFailure(TxBroadcastFailed(err)))
  }

  private def finishWith(result: Any): Unit = {
    collaborators.resultListener ! result
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
