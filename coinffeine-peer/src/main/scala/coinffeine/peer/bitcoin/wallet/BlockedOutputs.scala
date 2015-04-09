package coinffeine.peer.bitcoin.wallet

import scala.annotation.tailrec

import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.syntax.std.option._

import coinffeine.model.bitcoin.Hash
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.bitcoin.wallet.WalletActor.{NotEnoughFunds, UnknownFunds, FundsUseException}

private class BlockedOutputs {
  import BlockedOutputs.Output

  private class BlockedFunds(val blocked: Set[Output], var used: Set[Output] = Set.empty) {

    def canUse(amount: Bitcoin.Amount): Validation[NotEnoughFunds, Set[Output]] =
      collectFundsGreedily(amount, blocked.toSeq).toSuccess(NotEnoughFunds(amount, reservedAmount))

    def use(outputsToUse: Set[Output]): Unit = {
      require(outputsToUse.subsetOf(blocked) && used.intersect(outputsToUse).isEmpty)
      used ++= outputsToUse
    }

    def cancelUsage(outputs: Set[Output]): Unit = {
      val revertedOutputs = used.intersect(outputs)
      used --= revertedOutputs
    }

    def reservedAmount: Bitcoin.Amount = sumOutputs(blocked)
  }

  private var spendableOutputs = Set.empty[Output]
  private var blockedFunds = Map.empty[ExchangeId, BlockedFunds]

  def minOutput: Option[Bitcoin.Amount] =
    spendableAndNotBlocked.toSeq.map(_.value).sortBy(_.value).headOption

  def blocked: Bitcoin.Amount = sumOutputs(blockedOutputs)

  def available: Bitcoin.Amount = spendable - blocked

  def spendable: Bitcoin.Amount = sumOutputs(spendableOutputs)

  def setSpendCandidates(spendCandidates: Set[Output]): Unit = {
    spendableOutputs = spendCandidates
  }

  def collectFunds(amount: Bitcoin.Amount): Option[Set[Output]] =
    collectFundsGreedily(amount, spendableAndNotBlocked.toSeq)

  def areBlocked(coinsId: ExchangeId): Boolean = blockedFunds.contains(coinsId)

  def block(coinsId: ExchangeId, funds: Set[Output]): Unit = {
    require(!areBlocked(coinsId), s"$coinsId funds are already blocked")
    blockedFunds += coinsId -> new BlockedFunds(funds)
  }

  def unblock(id: ExchangeId): Unit = {
    blockedFunds -= id
  }

  def canUse(id: ExchangeId, amount: Bitcoin.Amount): Validation[FundsUseException, Set[Output]] = for {
    funds <- blockedFunds.get(id).toSuccess(UnknownFunds)
    outputs <- funds.canUse(amount)
  } yield outputs

  def use(id: ExchangeId, outputs: Set[Output]): Unit = {
    require(areBlocked(id))
    blockedFunds(id).use(outputs)
  }

  def collectUnblockedFunds(amount: Bitcoin.Amount): Option[Set[Output]] =
    collectFunds(amount)

  def cancelUsage(outputs: Set[Output]): Unit = {
    blockedFunds.values.foreach(_.cancelUsage(outputs))
  }

  @tailrec
  private def collectFundsGreedily(remainingAmount: Bitcoin.Amount,
                                   candidates: Seq[Output],
                                   alreadyCollected: Set[Output] = Set.empty): Option[Set[Output]] = {
    if (!remainingAmount.isPositive) Some(alreadyCollected)
    else candidates match  {
      case Seq() => None
      case Seq(candidate, remainingCandidates @ _*) =>
        collectFundsGreedily(
          remainingAmount - candidate.value,
          remainingCandidates,
          alreadyCollected + candidate
        )
    }
  }

  private def spendableAndNotBlocked = spendableOutputs diff blockedOutputs

  private def blockedOutputs: Set[Output] = blockedFunds.values.flatMap(_.blocked).toSet

  private def sumOutputs(outputs: Set[Output]): Bitcoin.Amount = outputs.toSeq.map(_.value).sum
}

private object BlockedOutputs {
  case class Output(txHash: Hash, index: Int, value: Bitcoin.Amount)
}
