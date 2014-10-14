package coinffeine.peer.bitcoin.wallet

import scala.annotation.tailrec

import scalaz.{Scalaz, Validation}

import org.bitcoinj.core.TransactionOutPoint

import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.bitcoin.wallet.WalletActor.{NotEnoughFunds, UnknownFunds, FundsUseException}

private class BlockedOutputs {
  import Scalaz._

  type Outputs = Set[TransactionOutPoint]

  private class BlockedFunds(var reservedOutputs: Outputs = Set.empty,
                             var usedOutputs: Outputs = Set.empty) {

    def canUse(amount: Bitcoin.Amount): Validation[NotEnoughFunds, Outputs] = {
      collectFundsGreedily(amount, reservedOutputs.toSeq)
        .toSuccess(NotEnoughFunds(amount, reservedAmount))
    }

    def use(outputsToUse: Outputs): Unit = {
      require(outputsToUse.subsetOf(reservedOutputs))
      reservedOutputs --= outputsToUse
      usedOutputs ++= outputsToUse
    }

    def cancelUsage(outputs: Outputs): Unit = {
      val revertedOutputs = usedOutputs.intersect(outputs)
      reservedOutputs ++= revertedOutputs
      usedOutputs --= revertedOutputs
    }

    def reservedAmount: Bitcoin.Amount = reservedOutputs.toSeq.map(outputValue).sum
  }

  private var spendableOutputs = Set.empty[TransactionOutPoint]
  private var blockedFunds = Map.empty[ExchangeId, BlockedFunds]

  def minOutput: Option[Bitcoin.Amount] =
    spendableAndNotBlocked.toSeq.map(outputValue).sortBy(_.value).headOption

  def blocked: Bitcoin.Amount = sumOutputs(blockedOutputs)

  def available: Bitcoin.Amount = spendable - blocked

  def spendable: Bitcoin.Amount = sumOutputs(spendableOutputs)

  def setSpendCandidates(spendCandidates: Outputs): Unit = {
    spendableOutputs = spendCandidates
  }

  def collectFunds(amount: Bitcoin.Amount): Option[Outputs] =
    collectFundsGreedily(amount, spendableAndNotBlocked.toSeq)

  def areBlocked(coinsId: ExchangeId): Boolean = blockedFunds.contains(coinsId)

  def block(coinsId: ExchangeId, funds: Outputs): Unit = {
    require(!areBlocked(coinsId), s"$coinsId funds are already blocked")
    blockedFunds += coinsId -> new BlockedFunds(funds)
  }

  def unblock(id: ExchangeId): Unit = {
    blockedFunds -= id
  }

  def canUse(id: ExchangeId, amount: Bitcoin.Amount): Validation[FundsUseException, Outputs] = for {
    funds <- blockedFunds.get(id).toSuccess(UnknownFunds)
    outputs <- funds.canUse(amount)
  } yield outputs

  def use(id: ExchangeId, outputs: Outputs): Unit = {
    require(areBlocked(id))
    blockedFunds(id).use(outputs)
  }

  def collectUnblockedFunds(amount: Bitcoin.Amount): Option[Outputs] = collectFunds(amount)

  def cancelUsage(outputs: Outputs): Unit = {
    blockedFunds.values.foreach(_.cancelUsage(outputs))
  }

  @tailrec
  private def collectFundsGreedily(remainingAmount: Bitcoin.Amount,
                                   candidates: Seq[TransactionOutPoint],
                                   alreadyCollected: Outputs = Set.empty): Option[Outputs] = {
    if (!remainingAmount.isPositive) Some(alreadyCollected)
    else candidates match  {
      case Seq() => None
      case Seq(candidate, remainingCandidates @ _*) =>
        collectFundsGreedily(
          remainingAmount - outputValue(candidate),
          remainingCandidates,
          alreadyCollected + candidate
        )
    }
  }

  private def spendableAndNotBlocked = spendableOutputs diff blockedOutputs

  private def blockedOutputs: Outputs = blockedFunds.values.flatMap(_.reservedOutputs).toSet

  private def sumOutputs(outputs: Outputs): Bitcoin.Amount = outputs.toSeq.map(outputValue).sum

  private def outputValue(output: TransactionOutPoint): Bitcoin.Amount =
    output.getConnectedOutput.getValue
}
