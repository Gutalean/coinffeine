package coinffeine.peer.bitcoin.wallet

import scala.annotation.tailrec

import coinffeine.model.bitcoin.MutableTransactionOutput
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId

private class BlockedOutputs {
  import coinffeine.peer.bitcoin.wallet.BlockedOutputs._
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

  private var spendableOutputs = Set.empty[MutableTransactionOutput]
  private var blockedFunds = Map.empty[ExchangeId, BlockedFunds]

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

  def block(coinsId: ExchangeId, amount: Bitcoin.Amount): Option[ExchangeId] = {
    if (blockedFunds.contains(coinsId)) None
    else collectFunds(amount).map { funds =>
      blockedFunds += coinsId -> new BlockedFunds(funds)
      coinsId
    }
  }

  def unblock(id: ExchangeId): Unit = {
    blockedFunds -= id
  }

  @throws[BlockedOutputs.FundsUseException]
  def use(id: ExchangeId, amount: Bitcoin.Amount): Outputs = {
    val funds = blockedFunds.getOrElse(id, throw UnknownFunds(id))
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

  private def sumOutputs(outputs: Outputs): Bitcoin.Amount =
    outputs.foldLeft(Bitcoin.Zero)(_ + _.getValue)
}

private object BlockedOutputs {
  sealed abstract class FundsUseException(message: String, cause: Throwable = null)
    extends Exception(message, cause)
  case class UnknownFunds(unknownId: ExchangeId)
    extends FundsUseException(s"Unknown coins id $unknownId")
  case class NotEnoughFunds(requested: Bitcoin.Amount, available: Bitcoin.Amount)
    extends FundsUseException(
      s"Not enough funds blocked: $requested requested, $available available")
}
