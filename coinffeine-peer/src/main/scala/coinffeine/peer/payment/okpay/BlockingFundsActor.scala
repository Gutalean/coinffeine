package coinffeine.peer.payment.okpay

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.payment.PaymentProcessorActor

class BlockingFundsActor extends Actor with ActorLogging {
  import coinffeine.peer.payment.okpay.BlockingFundsActor._

  private case class BlockedFundsInfo[C <: FiatCurrency](
      id: BlockedFundsId, remainingAmount: CurrencyAmount[C], listener: ActorRef) {

    def canUseFunds(amount: CurrencyAmount[C]): Boolean = amount <= remainingAmount
  }

  private var nextId = 1
  private var balances: Map[FiatCurrency, FiatAmount] = Map.empty
  private var funds: Map[BlockedFundsId, BlockedFundsInfo[_ <: FiatCurrency]] = Map.empty
  private var backedFunds = Set.empty[BlockedFundsId]
  private var notBackedFunds = Set.empty[BlockedFundsId]
  private var neverBackedFunds = Set.empty[BlockedFundsId]

  override def receive: Receive = {
    case RetrieveBlockedFunds(currency) =>
      totalBlockedForCurrency(currency) match {
        case Some(blockedFunds) => sender ! BlockingFundsActor.BlockedFunds(blockedFunds)
        case None => sender ! BlockingFundsActor.BlockedFunds(currency.Zero)
      }

    case BalancesUpdate(newBalances) =>
      balances = newBalances.map(b => b.currency -> b).toMap
      updateBackedFunds()

    case UseFunds(fundsId, amount) =>
      useFunds(fundsId, amount, sender())
      updateBackedFunds()

    case PaymentProcessorActor.BlockFunds(amount) =>
      val fundsId = generateFundsId()
      sender() ! fundsId
      funds += fundsId -> BlockedFundsInfo(fundsId, amount, sender())
      setNeverBacked(fundsId)
      updateBackedFunds()

    case PaymentProcessorActor.UnblockFunds(fundsId) =>
      funds -= fundsId
      clearBacked(fundsId)
      updateBackedFunds()
  }

  private def useFunds[C <: FiatCurrency](fundsId: BlockedFundsId,
                                          amount: CurrencyAmount[C],
                                          requester: ActorRef): Unit = {
    funds.get(fundsId) match {
      case Some(blockedFunds) if blockedFunds.remainingAmount.currency != amount.currency =>
        throw new IllegalArgumentException(s"Cannot use $amount out of ${blockedFunds.remainingAmount}")
      case Some(blockedFunds: BlockedFundsInfo[C]) =>
        if (amount > blockedFunds.remainingAmount) {
          requester ! CannotUseFunds(fundsId, amount,
            s"insufficient blocked funds for id $fundsId: " +
              s"$amount requested, ${blockedFunds.remainingAmount} available")
        } else if (!areBacked(blockedFunds)) {
          requester ! CannotUseFunds(
            fundsId, amount, s"funds with id $fundsId are not currently blocked")
        } else {
          updateFunds(blockedFunds.copy(remainingAmount = blockedFunds.remainingAmount - amount))
          reduceBalance(amount)
          requester ! FundsUsed(fundsId, amount)
        }
      case None =>
        requester ! CannotUseFunds(fundsId, amount, s"no such funds with id $fundsId")
    }
  }

  private def areBacked(blockedFunds: BlockedFundsInfo[_]): Boolean =
    backedFunds.contains(blockedFunds.id)

  private def updateFunds(newFunds: BlockedFundsInfo[_ <: FiatCurrency]): Unit = {
    funds += newFunds.id -> newFunds
  }

  private def reduceBalance[C <: FiatCurrency](amount: CurrencyAmount[C]): Unit = {
    val prevAmount = balances.getOrElse(amount.currency, CurrencyAmount.zero(amount.currency))
      .asInstanceOf[CurrencyAmount[C]]
    require(amount <= prevAmount)
    balances += amount.currency -> (prevAmount - amount)
  }

  private def updateBackedFunds(): Unit = {
    for (currency <- currenciesInUse()) {
      updateBackedFunds(currency)
    }
  }

  private def updateBackedFunds(currency: FiatCurrency): Unit = {
    val newlyBacked = fundsThatCanBeBacked(currency)
    val previouslyBacked = filterForCurrency(currency, backedFunds)
    val neverBacked = filterForCurrency(currency, neverBackedFunds)

    notifyAvailabilityChanges(neverBacked, previouslyBacked, newlyBacked)

    fundsForCurrency(currency).foreach(clearBacked)
    newlyBacked.foreach(setBacked)
  }

  private def fundsThatCanBeBacked[C <: FiatCurrency](currency: C): Set[BlockedFundsId] = {
    val availableBalance = balances.getOrElse(currency, currency.Zero)
    val eligibleFunds = funds
      .values
      .filter(_.remainingAmount.currency == currency)
      .asInstanceOf[Iterable[BlockedFundsInfo[C]]]
      .toSeq
      .sortBy(_.id.underlying)
    val fundsThatCanBeBacked =
      eligibleFunds.scanLeft(CurrencyAmount.zero(currency))(_ + _.remainingAmount)
        .takeWhile(_ <= availableBalance)
        .size - 1
    eligibleFunds.take(fundsThatCanBeBacked).map(_.id).toSet
  }

  private def notifyAvailabilityChanges(neverBacked: Set[BlockedFundsId],
                                        previouslyAvailable: Set[BlockedFundsId],
                                        currentlyAvailable: Set[BlockedFundsId]): Unit = {
    notifyListeners(
      PaymentProcessorActor.UnavailableFunds,
      (previouslyAvailable ++ neverBacked).diff(currentlyAvailable))
    notifyListeners(
      PaymentProcessorActor.AvailableFunds,
      currentlyAvailable.diff(previouslyAvailable))
  }

  private def notifyListeners(messageBuilder: BlockedFundsId => Any, fundsIds: Iterable[BlockedFundsId]): Unit = {
    for {
      fundsId <- fundsIds
      BlockedFundsInfo(_, _, listener) <- funds.get(fundsId)
    } {
      listener ! messageBuilder(fundsId)
    }
  }

  private def currenciesInUse(): Set[FiatCurrency] =
    funds.values.map(_.remainingAmount.currency).toSet

  private def generateFundsId() = {
    val id = BlockedFundsId(nextId)
    nextId += 1
    id
  }

  private def setBacked(funds: BlockedFundsId): Unit = {
    backedFunds += funds
  }

  private def setNotBacked(funds: BlockedFundsId): Unit = {
    notBackedFunds += funds
  }

  private def setNeverBacked(funds: BlockedFundsId): Unit = {
    neverBackedFunds += funds
  }

  private def clearBacked(funds: BlockedFundsId): Unit = {
    backedFunds -= funds
    notBackedFunds -= funds
    neverBackedFunds -= funds
  }

  private def fundsForCurrency(currency: FiatCurrency): Set[BlockedFundsId] =
    funds.values.filter(_.remainingAmount.currency == currency).map(_.id).toSet

  private def filterForCurrency(currency: FiatCurrency, funds: Set[BlockedFundsId]): Set[BlockedFundsId] = {
    val currencyFunds = fundsForCurrency(currency)
    funds.filter(currencyFunds.contains)
  }

  private def totalBlockedForCurrency[C <: FiatCurrency](currency: C): Option[FiatAmount] = {
    val fundsForCurrency = funds.values
      .filter(_.remainingAmount.currency == currency)
      .asInstanceOf[Iterable[BlockedFundsInfo[C]]]
    if (fundsForCurrency.isEmpty) None
    else Some(fundsForCurrency.map(_.remainingAmount).reduce(_ + _))
  }
}

object BlockingFundsActor {

  case class RetrieveBlockedFunds[C <: FiatCurrency](currency: C)
  case class BlockedFunds[C <: FiatCurrency](funds: CurrencyAmount[C])

  case class BalancesUpdate(balances: Seq[FiatAmount])

  case class UseFunds(funds: BlockedFundsId, amount: FiatAmount)
  case class FundsUsed(funds: BlockedFundsId, amount: FiatAmount)
  case class CannotUseFunds(funds: BlockedFundsId, amount: FiatAmount, reason: String)

  def props = Props(new BlockingFundsActor)
}
