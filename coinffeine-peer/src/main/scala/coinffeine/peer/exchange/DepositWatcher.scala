package coinffeine.peer.exchange

import scala.collection.JavaConverters._

import akka.actor.{Actor, ActorRef}
import com.google.bitcoin.core.TransactionOutPoint

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction, MutableTransactionOutput}
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.exchange.RunningExchange
import coinffeine.peer.bitcoin.blockchain.BlockchainActor

/** Actor that monitors a multisig deposit to inform about its destination */
class DepositWatcher(exchange: RunningExchange[_ <: FiatCurrency],
                     refundTx: ImmutableTransaction,
                     collaborators: DepositWatcher.Collaborators) extends Actor {

  private val myDeposit = exchange.role.select(exchange.state.deposits)
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
      val expectedAmount = exchange.role.select(exchange.amounts.finalStep.depositSplit)
      val depositUse = if (actualAmount < expectedAmount)
        DepositWatcher.WrongDepositUse(expectedAmount - actualAmount)
      else DepositWatcher.ChannelCompletion
      collaborators.listener ! DepositWatcher.DepositSpent(spendTx, depositUse)
  }

  private def amountForMe(tx: MutableTransaction): BitcoinAmount = {
    val userOutputs =
      for (output <- tx.getOutputs.asScala if sentToUserKey(output))
      yield Bitcoin.fromSatoshi(output.getValue)
    userOutputs.foldLeft(Bitcoin.Zero)(_ + _)
  }

  private def sentToUserKey(output: MutableTransactionOutput): Boolean = {
    val script = output.getScriptPubKey
    script.isSentToAddress && script.getToAddress(network) == userAddress
  }
}

object DepositWatcher {
  case class Collaborators(blockchain: ActorRef, listener: ActorRef)

  sealed trait DepositDestination
  case object DepositRefund extends DepositDestination
  case object ChannelCompletion extends DepositDestination
  case class WrongDepositUse(amountLost: BitcoinAmount) extends DepositDestination

  case class DepositSpent(tx: ImmutableTransaction, use: DepositDestination)
}

