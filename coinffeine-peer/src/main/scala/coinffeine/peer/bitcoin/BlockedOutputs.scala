package coinffeine.peer.bitcoin

import scala.annotation.tailrec

import coinffeine.model.bitcoin.MutableTransactionOutput
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.peer.bitcoin.WalletActor.CoinsId

private[bitcoin] class BlockedOutputs {
  import coinffeine.peer.bitcoin.BlockedOutputs._
  type Outputs = Set[MutableTransactionOutput]

  private class BlockedFunds(var reservedOutputs: Outputs = Set.empty,
                             var usedOutputs: Outputs = Set.empty) {
    def use(amount: BitcoinAmount): Outputs = {
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

    def reservedAmount: BitcoinAmount = reservedOutputs.toSeq
      .map(o => Bitcoin.fromSatoshi(o.getValue))
      .foldLeft(Bitcoin.Zero)(_ + _)
  }

  private var nextId = 1
  private var spendableOutputs = Set.empty[MutableTransactionOutput]
  private var blockedFunds = Map.empty[CoinsId, BlockedFunds]

  def setSpendCandidates(spendCandidates: Outputs): Unit = {
    spendableOutputs = spendCandidates
  }

  def block(amount: BitcoinAmount): Option[CoinsId] = {
    collectFunds(amount).map { funds =>
      val coinsId = generateNextCoinsId()
      blockedFunds += coinsId -> new BlockedFunds(funds)
      coinsId
    }
  }

  def unblock(id: CoinsId): Unit = {
    blockedFunds -= id
  }

  @throws[BlockedOutputs.BlockingFundsException]
  def use(id: CoinsId, amount: BitcoinAmount): Outputs = {
    val funds = blockedFunds.getOrElse(id, throw UnknownCoinsId(id))
    funds.use(amount)
  }

  def cancelUsage(outputs: Outputs): Unit = {
    blockedFunds.values.foreach(_.cancelUsage(outputs))
  }

  private def collectFunds(amount: BitcoinAmount): Option[Outputs] = {
    val spendableAndNotBlocked = spendableOutputs diff blockedOutputs
    collectFundsGreedily(amount, spendableAndNotBlocked.toSeq)
  }

  @tailrec
  private def collectFundsGreedily(remainingAmount: BitcoinAmount,
                                   candidates: Seq[MutableTransactionOutput],
                                   alreadyCollected: Outputs = Set.empty): Option[Outputs] = {
    if (!remainingAmount.isPositive) Some(alreadyCollected)
    else candidates match  {
      case Seq() => None
      case Seq(candidate, remainingCandidates @ _*) =>
        collectFundsGreedily(
          remainingAmount - Bitcoin.fromSatoshi(candidate.getValue),
          remainingCandidates,
          alreadyCollected + candidate
        )
    }
  }

  private def blockedOutputs: Outputs = blockedFunds.values.flatMap(_.reservedOutputs).toSet

  private def generateNextCoinsId() = {
    val coinsId = CoinsId(nextId)
    nextId += 1
    coinsId
  }
}

object BlockedOutputs {
  sealed abstract class BlockingFundsException(message: String, cause: Throwable = null)
    extends Exception(message, cause)
  case class UnknownCoinsId(unknownId: CoinsId)
    extends BlockingFundsException(s"Unknown coins id $unknownId")
  case class NotEnoughFunds(requested: BitcoinAmount, available: BitcoinAmount)
    extends BlockingFundsException(
      s"Not enough funds blocked: $requested requested, $available available")
}
