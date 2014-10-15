package coinffeine.peer.exchange.broadcast

import scala.concurrent.duration._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.broadcast.ExchangeTransactionBroadcaster._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.LastBroadcastableOffer

private class DefaultExchangeTransactionBroadcaster(bitcoinPeerActor: ActorRef,
                                                    blockchain: ActorRef,
                                                    constants: ProtocolConstants)
  extends Actor with ActorLogging with Stash {

  override val receive: Receive = {
    case msg: StartBroadcastHandling =>
      new InitializedBroadcastActor(msg, blockchain).start()
  }

  private class InitializedBroadcastActor(init: StartBroadcastHandling, blockchain: ActorRef) {
    import init._
    import DefaultExchangeTransactionBroadcaster.ReadyForBroadcast

    private var lastOffer: Option[ImmutableTransaction] = None

    def start(): Unit = {
      setTimePanicFinish()
      context.become(handleFinishExchange)
    }

    private def setTimePanicFinish(): Unit = {
      val panicBlock = refund.get.getLockTime - constants.refundSafetyBlockCount
      autoNotifyBlockchainHeightWith(panicBlock, PublishBestTransaction)
    }

    private val handleFinishExchange: Receive = {
      case LastBroadcastableOffer(tx) =>
        lastOffer = Some(tx)

      case PublishBestTransaction =>
        val bestOffer = lastOffer.getOrElse(refund).get
        if (bestOffer.isTimeLocked) {
          autoNotifyBlockchainHeightWith(bestOffer.getLockTime, ReadyForBroadcast)
        } else {
          self ! ReadyForBroadcast
        }
        context.become(readyForBroadcast(ImmutableTransaction(bestOffer)))
    }

    private def readyForBroadcast(offer: ImmutableTransaction): Receive = {
      case ReadyForBroadcast =>
        bitcoinPeerActor ! PublishTransaction(offer)
        context.become(broadcastCompleted(offer))
    }

    private def broadcastCompleted(txToPublish: ImmutableTransaction): Receive = {
      case msg @ TransactionPublished(`txToPublish`, _) =>
        finishWith(SuccessfulBroadcast(msg))
      case TransactionPublished(_, unexpectedTx) =>
        finishWith(FailedBroadcast(UnexpectedTxBroadcast(unexpectedTx)))
      case TransactionNotPublished(_, err) =>
        finishWith(FailedBroadcast(err))
    }

    private def finishWith(result: Any): Unit = {
      resultListeners.foreach { _ ! result}
      context.stop(self)
    }

    private def autoNotifyBlockchainHeightWith(height: Long, msg: Any): Unit = {
      import context.dispatcher
      implicit val timeout = Timeout(1.day)
      (blockchain ? BlockchainActor.WatchBlockchainHeight(height))
        .mapTo[BlockchainActor.BlockchainHeightReached]
        .onSuccess { case _ =>
        self ! msg
      }
    }
  }
}


object DefaultExchangeTransactionBroadcaster {

  def props(bitcoinPeerActor: ActorRef, blockchain: ActorRef, constants: ProtocolConstants) =
    Props(new DefaultExchangeTransactionBroadcaster(bitcoinPeerActor, blockchain, constants))

  private case object ReadyForBroadcast
}
