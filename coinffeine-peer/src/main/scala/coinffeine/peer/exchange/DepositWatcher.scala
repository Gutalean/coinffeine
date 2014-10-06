package coinffeine.peer.exchange

import scala.collection.JavaConverters._

import akka.actor.{Actor, ActorRef}
import org.bitcoinj.core.TransactionOutPoint

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction, MutableTransactionOutput}
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor

/** Actor that monitors a multisig deposit to inform about its destination */
class DepositWatcher(exchange: HandshakingExchange[_ <: FiatCurrency],
                     myDeposit: ImmutableTransaction,
                     refundTx: ImmutableTransaction,
                     collaborators: DepositWatcher.Collaborators) extends Actor {

  private val network = myDeposit.get.getParams
  private val userAddress = exchange.state.user.bitcoinKey.toAddress(network)

  override def preStart(): Unit = {
    val tx = myDeposit.get
    val output = new TransactionOutPoint(network, 0, tx.getHash)
    collaborators.blockchain ! BlockchainActor.WatchOutput(output)
  }

  override def receive: Receive = {
    case BlockchainActor.OutputSpent(_, `refundTx`) =>
      collaborators.listener ! DepositWatcher.DepositSpent(refundTx, DepositWatcher.DepositRefund)

    case BlockchainActor.OutputSpent(_, spendTx) =>
      val actualAmount = amountForMe(spendTx.get)
      val depositUse = stepWithAmount(actualAmount) match {
        case None => DepositWatcher.UnexpectedDestination
        case Some(finalStep) if finalStep == exchange.amounts.steps.size =>
          DepositWatcher.CompletedChannel
        case Some(intermediateStep) => DepositWatcher.ChannelAtStep(intermediateStep)
      }
      collaborators.listener ! DepositWatcher.DepositSpent(spendTx, depositUse)
  }

  private def amountForMe(tx: MutableTransaction): Bitcoin.Amount =
    (for (output <- tx.getOutputs.asScala if sentToUserKey(output))
      yield Bitcoin.fromSatoshi(output.getValue.value)).sum

  private def sentToUserKey(output: MutableTransactionOutput): Boolean = {
    val script = output.getScriptPubKey
    script.isSentToAddress && script.getToAddress(network) == userAddress
  }

  private def stepWithAmount(amount: Bitcoin.Amount): Option[Int] =
    exchange.amounts.steps.zipWithIndex.reverse.collectFirst {
      case (step, index) if exchange.role.select(step.depositSplit) == amount => index + 1
    }
}

object DepositWatcher {
  case class Collaborators(blockchain: ActorRef, listener: ActorRef)

  sealed trait DepositDestination
  case object DepositRefund extends DepositDestination
  case object CompletedChannel extends DepositDestination
  case class ChannelAtStep(step: Int) extends DepositDestination
  case object UnexpectedDestination extends DepositDestination

  case class DepositSpent(tx: ImmutableTransaction, use: DepositDestination)
}

