package coinffeine.peer.exchange

import akka.actor._

import coinffeine.model.bitcoin.{Hash, ImmutableTransaction, Wallet}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor.{BlockchainActorReference, RetrieveBlockchainActor, TransactionPublished}
import coinffeine.peer.bitcoin.BlockchainActor._
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.ExchangeTransactionBroadcastActor.{UnexpectedTxBroadcast => _, _}
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.StartMicroPaymentChannel
import coinffeine.peer.exchange.protocol._

class ExchangeActor[C <: FiatCurrency](
    handshakeActorProps: Props,
    microPaymentChannelActorProps: Props,
    transactionBroadcastActorProps: Props,
    exchangeProtocol: ExchangeProtocol,
    constants: ProtocolConstants,
    resultListeners: Set[ActorRef]) extends Actor with ActorLogging {

  val receive: Receive = {
    case init: StartExchange[C] => new InitializedExchange(init).start()
  }

  private class InitializedExchange(init: StartExchange[C]) {
    import init._
    private var blockchain: ActorRef = _
    private var _txBroadcaster: ActorRef = _
    private def txBroadcaster = Option(_txBroadcaster).getOrElse {
      val message = "Transaction broadcast actor does not exist"
      log.error(message)
      throw new Error(message)
    }

    def start(): Unit = {
      require(userWallet.getKeys.contains(user.bitcoinKey))
      log.info(s"Starting exchange ${exchange.id}")
      bitcoinPeer ! RetrieveBlockchainActor
      context.become(retrievingBlockchain)
    }

    private val inHandshake: Receive = {
      case HandshakeSuccess(handshakingExchange: HandshakingExchange[C], commitmentTxIds, refundTx) =>
        context.child(HandshakeActorName).map(context.stop)
        _txBroadcaster = context.actorOf(
          transactionBroadcastActorProps, TransactionBroadcastActorName)
        watchForDepositKeys(handshakingExchange)
        txBroadcaster ! StartBroadcastHandling(refundTx, bitcoinPeer, resultListeners = Set(self))
        commitmentTxIds.toSeq.foreach(id => blockchain ! RetrieveTransaction(id))
        context.become(receiveTransaction(handshakingExchange, commitmentTxIds))
      case HandshakeFailure(err) => finishWith(ExchangeFailure(err))
    }

    private val retrievingBlockchain: Receive = {
      case BlockchainActorReference(blockchainRef) =>
        blockchain = blockchainRef
        startHandshake()
        context.become(inHandshake)
    }

    private def startHandshake(): Unit = {
      // TODO: ask the wallet actor for funds
      val funds = UnspentOutput.collect(role.myDepositAmount(exchange.amounts), userWallet)
      context.actorOf(handshakeActorProps, HandshakeActorName) ! StartHandshake(
        exchange, role, user, funds, userWallet.getChangeAddress, constants,
        messageGateway, blockchain, resultListeners = Set(self)
      )
    }

    private def finishingExchange(
        result: Any, expectedFinishingTx: Option[ImmutableTransaction]): Receive = {
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

    private val inMicropaymentChannel: Receive = {
      case MicroPaymentChannelActor.ExchangeSuccess(successTx) =>
        log.info(s"Finishing exchange '${exchange.id}' successfully")
        txBroadcaster ! FinishExchange
        context.become(finishingExchange(ExchangeSuccess, successTx))
      case MicroPaymentChannelActor.ExchangeFailure(e) =>
        log.warning(s"Finishing exchange '${exchange.id}' with a failure due to ${e.toString}")
        txBroadcaster ! FinishExchange
        context.become(finishingExchange(ExchangeFailure(e), None))
      case ExchangeFinished(TransactionPublished(_, broadcastTx)) =>
        finishWith(ExchangeFailure(RiskOfValidRefund(broadcastTx)))
    }

    private def receiveTransaction(handshakingExchange: HandshakingExchange[C],
                                   commitmentTxIds: Both[Hash]): Receive = {
      def withReceivedTxs(receivedTxs: Map[Hash, ImmutableTransaction]): Receive = {
        case TransactionFound(id, tx) =>
          val newTxs = receivedTxs.updated(id, tx)
          if (commitmentTxIds.toSeq.forall(newTxs.keySet.contains)) {
            // TODO: what if counterpart deposit is not valid?
            val deposits = exchangeProtocol.validateDeposits(commitmentTxIds.map(newTxs), handshakingExchange).get
            val runningExchange = RunningExchange(deposits, handshakingExchange)
            val ref = context.actorOf(microPaymentChannelActorProps, MicroPaymentChannelActorName)
            ref ! StartMicroPaymentChannel[C](runningExchange, constants,
              paymentProcessor, messageGateway, resultListeners = Set(self))
            txBroadcaster ! SetMicropaymentActor(ref)
            context.become(inMicropaymentChannel)
          } else {
            context.become(withReceivedTxs(newTxs))
          }
        case TransactionNotFound(txId) =>
          finishWith(ExchangeFailure(CommitmentTxNotInBlockChain(txId)))
      }
      withReceivedTxs(Map.empty)
    }

    private def finishWith(result: Any): Unit = {
      resultListeners.foreach { _ ! result }
      context.stop(self)
    }

    private def watchForDepositKeys(ongoingExchange: OngoingExchange[C]): Unit = {
      ongoingExchange.participants.toSeq.foreach { p =>
        blockchain ! WatchPublicKey(p.bitcoinKey)
      }
    }
  }
}

/** This actor handles all the necessary steps for an exchange to happen */
object ExchangeActor {

  val HandshakeActorName = "handshake"
  val MicroPaymentChannelActorName = "exchange"
  val TransactionBroadcastActorName = "transactionBroadcast"

  /** This is a request for the actor to start the exchange */
  case class StartExchange[C <: FiatCurrency](
    exchange: Exchange[C],
    role: Role,
    user: Exchange.PeerInfo,
    userWallet: Wallet,
    paymentProcessor: ActorRef,
    messageGateway: ActorRef,
    bitcoinPeer: ActorRef
  )

  /** This is a message sent to the listeners to indicate that an exchange succeeded */
  case object ExchangeSuccess

  /** This is a message sent to the listeners to indicate that an exchange failed */
  case class ExchangeFailure(e: Throwable)

  case class CommitmentTxNotInBlockChain(txId: Hash) extends RuntimeException(
    s"Handshake reported that the commitment transaction with hash $txId was in " +
      s"blockchain but it could not be found")

  case class UnexpectedTxBroadcast(effectiveTx: ImmutableTransaction, expectedTx: ImmutableTransaction)
    extends RuntimeException(
      s"""The transaction broadcast for this exchange is different from the one that was being expected.
            |   Sent transaction: $effectiveTx
            |   Expected: $expectedTx""".stripMargin)

  case class TxBroadcastFailed(cause: Throwable) extends RuntimeException(
    "The final transaction could not be broadcast", cause)

  case class RiskOfValidRefund(broadcastTx: ImmutableTransaction) extends RuntimeException(
    "The exchange was forcefully finished because it was taking too long and there was a chance" +
      "that the refund transaction could have become valid"
  )
}
