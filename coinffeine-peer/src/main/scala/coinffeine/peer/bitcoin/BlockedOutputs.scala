package coinffeine.peer.bitcoin

import scala.annotation.tailrec

import coinffeine.model.bitcoin.{BlockedCoinsId, MutableTransactionOutput}
import coinffeine.model.currency._

private[bitcoin] class BlockedOutputs {
  import coinffeine.peer.bitcoin.BlockedOutputs._
  type Outputs = Set[MutableTransactionOutput]

  private class BlockedFunds(var reservedOutputs: Outputs = Set.empty,
                             var usedOutputs: Outputs = Set.empty) {
    def use(amount: Bitcoin.Amount): Outputs = {
      val outputsToUse = collectFundsGreedily(amount, reservedOutputs.toSeq)
        .getOrElse(throw NotEnoughFunds(amount, reservedAmount))
      reservedOutputs --= outputsToUse
      usedOutputs ++= outputsToUse
      outputsToUse
    }

    def cancelUsage(outputs: Outputs): Unit = {
      val revertedOutputs = usedOutputs.intersect(outputs)
      reservedOutputs ++= revertedOutputs
      usedOutputs --= revertedOutputs
    }

    def reservedAmount: Bitcoin.Amount = reservedOutputs.toSeq
      .map(o => Bitcoin(o.getValue.value)).sum
  }

  private var nextId = 1
  private var spendableOutputs = Set.empty[MutableTransactionOutput]
  private var blockedFunds = Map.empty[BlockedCoinsId, BlockedFunds]

  def minOutput: Option[Bitcoin.Amount] = spendableAndNotBlocked
    .toSeq
    .map(output => Bitcoin.fromSatoshi(output.getValue.value))
    .sortBy(_.value)
    .headOption

  def blocked: Bitcoin.Amount = sumOutputs(blockedOutputs)

  def available: Bitcoin.Amount = spendable - blocked

  def spendable: Bitcoin.Amount = sumOutputs(spendableOutputs)

  def setSpendCandidates(spendCandidates: Outputs): Unit = {
    spendableOutputs = spendCandidates
  }

  def block(amount: Bitcoin.Amount): Option[BlockedCoinsId] = {
    collectFunds(amount).map { funds =>
      val coinsId = generateNextCoinsId()
      blockedFunds += coinsId -> new BlockedFunds(funds)
      coinsId
    }
  }

  def unblock(id: BlockedCoinsId): Unit = {
    blockedFunds -= id
  }

  @throws[BlockedOutputs.BlockingFundsException]
  def use(id: BlockedCoinsId, amount: Bitcoin.Amount): Outputs = {
    val funds = blockedFunds.getOrElse(id, throw UnknownCoinsId(id))
    funds.use(amount)
  }

  def cancelUsage(outputs: Outputs): Unit = {
    blockedFunds.values.foreach(_.cancelUsage(outputs))
  }

  private def collectFunds(amount: Bitcoin.Amount): Option[Outputs] = {
    collectFundsGreedily(amount, spendableAndNotBlocked.toSeq)
  }

  @tailrec
  private def collectFundsGreedily(remainingAmount: Bitcoin.Amount,
                                   candidates: Seq[MutableTransactionOutput],
                                   alreadyCollected: Outputs = Set.empty): Option[Outputs] = {
    if (!remainingAmount.isPositive) Some(alreadyCollected)
    else candidates match  {
      case Seq() => None
      case Seq(candidate, remainingCandidates @ _*) =>
        collectFundsGreedily(
          remainingAmount - candidate.getValue,
          remainingCandidates,
          alreadyCollected + candidate
        )
    }
  }

  private def spendableAndNotBlocked = spendableOutputs diff blockedOutputs

  private def blockedOutputs: Outputs = blockedFunds.values.flatMap(_.reservedOutputs).toSet

  private def generateNextCoinsId() = {
    val coinsId = BlockedCoinsId(nextId)
    nextId += 1
    coinsId
  }

  private def sumOutputs(outputs: Outputs): Bitcoin.Amount =
    outputs.foldLeft(Bitcoin.Zero)(_ + _.getValue)
}

object BlockedOutputs {
  sealed abstract class BlockingFundsException(message: String, cause: Throwable = null)
    extends Exception(message, cause)
  case class UnknownCoinsId(unknownId: BlockedCoinsId)
    extends BlockingFundsException(s"Unknown coins id $unknownId")
  case class NotEnoughFunds(requested: Bitcoin.Amount, available: Bitcoin.Amount)
    extends BlockingFundsException(
      s"Not enough funds blocked: $requested requested, $available available")
}
