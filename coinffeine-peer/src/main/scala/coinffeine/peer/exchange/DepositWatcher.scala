package coinffeine.peer.exchange

import scala.collection.JavaConverters._

import akka.actor.{Actor, ActorRef}

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction, MutableTransactionOutput}
import coinffeine.model.currency.{Bitcoin, BitcoinAmount}
import coinffeine.model.exchange._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor

/** Actor that monitors a multisig deposit to inform about its destination */
class DepositWatcher(exchange: DepositPendingExchange,
                     myDeposit: ImmutableTransaction,
                     refundTx: ImmutableTransaction,
                     herDeposit: Option[ImmutableTransaction],
                     collaborators: DepositWatcher.Collaborators) extends Actor {

  private val network = exchange.parameters.network
  private val userAddress = exchange.user.bitcoinKey.toAddress(network)

  override def preStart(): Unit = {
    collaborators.blockchain ! BlockchainActor.WatchOutput(myDeposit.get.getOutput(0))
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

  private def amountForMe(tx: MutableTransaction): BitcoinAmount =
    (for (output <- tx.getOutputs.asScala if sentToUserKey(output))
      yield Bitcoin.fromSatoshi(output.getValue.value)).sum

  private def sentToUserKey(output: MutableTransactionOutput): Boolean = {
    val script = output.getScriptPubKey
    script.isSentToAddress && script.getToAddress(network) == userAddress
  }

  private def stepWithAmount(amount: BitcoinAmount): Option[Int] =
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

